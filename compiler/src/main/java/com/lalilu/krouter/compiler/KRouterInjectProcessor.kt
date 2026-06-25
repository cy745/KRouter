package com.lalilu.krouter.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.lalilu.krouter.InjectMap
import com.lalilu.krouter.annotation.Destination
import com.lalilu.krouter.annotation.KInject
import com.lalilu.krouter.annotation.KService
import com.lalilu.krouter.compiler.code.buildGetRouterMapFunc
import com.lalilu.krouter.compiler.code.buildHandleParamsFunction
import com.lalilu.krouter.compiler.code.buildParamStateClass
import com.lalilu.krouter.compiler.code.generateKInjectActualImplementations
import com.lalilu.krouter.compiler.code.handleServicesProperties
import com.lalilu.krouter.compiler.ext.asClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * 路由注入处理器：收集所有模块的 @Destination / @KService，生成 KRouterInjectMap。
 *
 * ## 跨模块收集流程
 *
 * ```
 *                        ┌─────────────────────────────┐
 *                        │  Module A (library)          │
 *                        │  kRouterType=collect         │
 *                        │  生成 KRouterMap_Metadata_A  │
 *                        └─────────────┬───────────────┘
 *                                      │ class file 在 A 的制品中
 *                        ┌─────────────▼───────────────┐
 *                        │  Module B (library)          │
 *                        │  kRouterType=collect         │
 *                        │  生成 KRouterMap_Metadata_B  │
 *                        └─────────────┬───────────────┘
 *                                      │ class file 在 B 的制品中
 *                        ┌─────────────▼───────────────┐
 *                        │  App (main module)           │
 *                        │  kRouterType=inject          │
 *                        │  通过 getDeclarationsFromPackage │
 *                        │  读取 A+B 的 metadata，汇总     │
 *                        │  生成 KRouterInjectMap        │
 *                        └─────────────────────────────┘
 * ```
 *
 * ## 多轮处理模式
 *
 * KSP 可能调用 process() 多次（多轮）。KMP Gradle 真实场景中：
 * - **第一轮**：生成 metadata 文件，返回 metadata 类触发下一轮
 * - **第二轮及以后**：metadata 已被 KSP 索引，从
 *   `getDeclarationsFromPackage(GENERATED_SHARED_PACKAGE)` 加载所有模块的收集结果
 *
 * kctfork 单轮测试中 metadata 来不及被索引，改用 `generatedFile.isNotEmpty()`
 * 回退到 `getSymbolsWithAnnotation()` 直接收集（同轮可见）。
 */
class KRouterInjectProcessor(
    environment: SymbolProcessorEnvironment
) : KRouterCollectProcessor(environment) {
    companion object {
        /** 生成的注入映射类名 */
        val className = "KRouterInjectMap"
    }

    /** 注入只执行一次，避免多轮重复生成 */
    private var injectPhaseDone = false

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (injectPhaseDone) return emptyList()

        // ── 第一轮：收集注解，生成 metadata（供依赖模块读取） ──
        val resultList = super.process(resolver)

        // ── 尝试读取跨模块 metadata ──
        // getDeclarationsFromPackage 在 metadata 文件被 KSP 索引后才能查到。
        // 第一轮刚生成文件时尚未索引 → 空；第二轮（Gradle KMP）→ 可读。
        val generatedClasses = resolver.getDeclarationsFromPackage(GENERATED_SHARED_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val (destinations, services, collectedMap) = when {
            // ── 多轮模式（Gradle KMP 真实构建） ──
            // metadata 已被索引 → 从中读取所有模块收集的类
            generatedClasses.isNotEmpty() -> {
                val propertiesItems = generatedClasses
                    .flatMap { it.getDeclaredProperties() }
                val collected = propertiesItems
                    .map { it.type.resolve().declaration.asClassDeclaration() }
                    .toList()
                Triple(
                    collected.filter { it.isAnnotationPresent(Destination::class) },
                    collected.filter { it.isAnnotationPresent(KService::class) },
                    collected
                )
            }

            // ── 单轮模式（kctfork 测试 / 第一轮回退） ──
            // metadata 生成但尚未索引，直接从 resolver 同轮收集
            environment.codeGenerator.generatedFile.isNotEmpty() -> {
                val dests = resolver.getSymbolsWithAnnotation(Destination::class.qualifiedName!!)
                    .map { it as KSClassDeclaration }
                    .toList()
                val svcs = resolver.getSymbolsWithAnnotation(KService::class.qualifiedName!!)
                    .map { it as KSClassDeclaration }
                    .toList()
                Triple(dests, svcs, dests + svcs)
            }

            // ── 无任何注解 ──
            // 生成空壳注入映射 TODO 支持完成
            else -> {
                writeKRouterInjectMap(
                    environment.codeGenerator, emptyList(), emptyList(), emptyList()
                )
                injectPhaseDone = true
                return emptyList()
            }
        }

        // ── 生成 KRouterInjectMap ──
        writeKRouterInjectMap(environment.codeGenerator, collectedMap, destinations, services)

        // ── 为 @KInject expect fun 生成 actual 实现 ──
        val kInjectFunctions = resolver.getSymbolsWithAnnotation(KInject::class.qualifiedName!!)
            .mapNotNull { it as? KSFunctionDeclaration }
            .filter { it.returnType?.resolve()?.declaration?.qualifiedName?.asString() == "com.lalilu.krouter.InjectMap" }
            .toList()

        if (kInjectFunctions.isNotEmpty()) {
            kInjectFunctions.generateKInjectActualImplementations(environment.codeGenerator)
        }

        injectPhaseDone = true

        return resolver.getClassDeclarationByName(className)
            ?.let { listOf(it) }
            ?: emptyList()
    }

    /** 写入 KRouterInjectMap 源码文件 */
    @OptIn(KspExperimental::class)
    private fun writeKRouterInjectMap(
        codeGenerator: CodeGenerator,
        collectedMap: List<KSClassDeclaration>,
        destinations: List<KSClassDeclaration>,
        services: List<KSClassDeclaration>
    ) {
        val classSpec = TypeSpec.objectBuilder(className)
            .addSuperinterface(InjectMap::class)
            .addKdoc(CLASS_KDOC)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build()
            )
            .addProperty(handleServicesProperties(services))
            .addFunction(buildGetRouterMapFunc(destinations))
            .addType(buildParamStateClass())
            .addFunction(buildHandleParamsFunction())
            .build()

        val fileSpec = FileSpec.builder(GENERATED_SHARED_PACKAGE, className)
            .addType(classSpec)
            .indent("    ")
            .build()

        val dependencies = collectedMap
            .mapNotNull { it.containingFile }
            .distinct()
            .toTypedArray()

        runCatching {
            fileSpec.writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(aggregating = true, *dependencies)
            )
        }.onFailure { e ->
            log("Failed to write generated inject map: ${e.message}")
        }
    }
}
