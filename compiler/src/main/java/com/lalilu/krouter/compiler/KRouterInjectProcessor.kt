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
 * ## 两轮处理时序
 *
 * ```
 *                             第一轮                             第二轮
 *                         ┌──────────────┐            ┌──────────────────────┐
 *  kRouterType=collect    │ super.process │            │                      │
 *  的各依赖模块           │ 生成 metadata │            │                      │
 *                         └──────┬───────┘            │                      │
 *                                │ metadata class     │                      │
 *                                ▼ 在依赖的制品中      │                      │
 *                         ┌──────────────────┐        │                      │
 *  kRouterType=inject     │ super.process()   │        │ super.process()      │
 *  的主模块               │ 生成自己的metadata │        │ 重新生成相同metadata  │
 *                         ├──────────────────┤        ├──────────────────────┤
 *                         │ @KInject actual   │        │ getDeclarationsFr…() │
 *                         │ （第一轮才可见）   │        │ 读取依赖的 metadata   │
 *                         ├──────────────────┤        ├──────────────────────┤
 *                         │ 返回 metadata 类  │        │ getSymbolsWithAnn…() │
 *                         │ → 触发第二轮      │        │ 取当前模块自己的注解  │
 *                         └──────────────────┘        ├──────────────────────┤
 *                                                     │ 合并 → 生成 InjectMap│
 *                                                     └──────────────────────┘
 * ```
 *
 * ## 为什么需要两轮？
 *
 * 1. **跨模块 metadata 可见性**：KSP 的 getDeclarationsFromPackage() 只返回已编译
 *    或已被 KSP 索引的声明。刚生成的文件在下一轮才可索引。
 * 2. **当前模块注解**：第二轮中当前模块自己的 metadata 刚被 super.process() 生成
 *    同轮不可见，需用 getSymbolsWithAnnotation() 补充。
 * 3. **@KInject 的特殊性**：第二轮 KSP 中 expect fun 不可见（KMP 限制），所以
 *    actual 生成必须在第一轮完成。
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
            // generatedClasses 包含依赖模块的 metadata（已编译，跨轮可见）
            // 当前模块自己的 metadata 由 super.process() 刚刚生成、同轮未索引，
            // 因此用 getSymbolsWithAnnotation 直接从 resolver 取当前模块的注解
            generatedClasses.isNotEmpty() -> {
                val ownDests = resolver.getSymbolsWithAnnotation(Destination::class.qualifiedName!!)
                    .map { it as KSClassDeclaration }
                val ownSvcs = resolver.getSymbolsWithAnnotation(KService::class.qualifiedName!!)
                    .map { it as KSClassDeclaration }

                val fromMetadata = generatedClasses
                    .flatMap { it.getDeclaredProperties() }
                    .map { it.type.resolve().declaration.asClassDeclaration() }

                val collected = (ownDests + ownSvcs + fromMetadata)
                    .distinct()
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
