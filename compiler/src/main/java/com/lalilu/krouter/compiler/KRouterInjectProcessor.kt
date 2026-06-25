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

    /** inject 阶段只执行一次（第二轮），避免多轮重复生成 */
    private var injectPhaseDone = false

    /** @KInject 只在第一轮处理（第二轮 KSP 中 expect fun 不可见） */
    private var kInjectDone = false

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (injectPhaseDone) return emptyList()

        // ── 第一轮：收集注解，生成 metadata（供依赖模块读取） ──
        val resultList = super.process(resolver)

        // ── @KInject 只在第一轮处理（第一轮 source 可见，第二轮取不到 expect fun） ──
        if (!kInjectDone) {
            processKInjectFunctions(resolver)
        }

        // ── 尝试读取跨模块 metadata ──
        // getDeclarationsFromPackage 在 metadata 文件被 KSP 索引后才能查到。
        // 第一轮刚生成文件时尚未索引 → 空；第二轮（Gradle KMP）→ 可读。
        val generatedClasses = resolver.getDeclarationsFromPackage(GENERATED_SHARED_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val (destinations, services, collectedMap) = when {
            // ── metadata 已被索引（第二轮+） ──
            // 从中读取当前模块及所有依赖模块的收集结果
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

            // ── 第一轮：metadata 已生成但尚未被 KSP 索引 ──
            // 返回 metadata 类触发第二轮，届时 getDeclarationsFromPackage 即可见
            environment.codeGenerator.generatedFile.isNotEmpty() -> {
                return resultList
            }

            // ── 无任何注解 ──
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

        injectPhaseDone = true

        return resolver.getClassDeclarationByName(className)
            ?.let { listOf(it) }
            ?: emptyList()
    }

    /** 收集 @KInject expect fun 并生成 actual 实现（仅在 KSP source 可见的第一轮执行） */
    private fun processKInjectFunctions(resolver: Resolver) {
        kInjectDone = true
        val functions = resolver.getSymbolsWithAnnotation(KInject::class.qualifiedName!!)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { func ->
                val returnType = func.returnType
                    ?.resolve()
                    ?.declaration
                    ?.qualifiedName
                    ?.asString()
                returnType == "com.lalilu.krouter.InjectMap"
            }
            .toList()

        if (functions.isNotEmpty()) {
            functions.generateKInjectActualImplementations(environment.codeGenerator)
        }
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
