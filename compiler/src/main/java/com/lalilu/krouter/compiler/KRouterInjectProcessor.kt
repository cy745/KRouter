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
import com.lalilu.krouter.annotation.Destination
import com.lalilu.krouter.annotation.KService
import com.lalilu.krouter.compiler.code.buildGetRouterMapFunc
import com.lalilu.krouter.compiler.code.buildHandleParamsFunction
import com.lalilu.krouter.compiler.code.buildParamStateClass
import com.lalilu.krouter.compiler.code.handleServicesProperties
import com.lalilu.krouter.compiler.ext.asClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * 真正实现路由注入的处理器，继承自KRouterCollectProcessor
 * 收集完所在模块后才会执行注入操作
 */
class KRouterInjectProcessor(
    environment: SymbolProcessorEnvironment
) : KRouterCollectProcessor(environment) {
    companion object {
        val className = "KRouterInjectMap"
    }

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val resultList = super.process(resolver)

        // 若存在生成的文件，则说明收集到了路由信息，还会触发一次process，此时可跳过注入操作
        if (environment.codeGenerator.generatedFile.isNotEmpty()) {
            return resultList
        }

        val generatedItems = resolver.getDeclarationsFromPackage(GENERATED_SHARED_PACKAGE)
        val propertiesItems = generatedItems
            .mapNotNull { (it as? KSClassDeclaration)?.getDeclaredProperties() }
            .flatten()

        val collectedMap = propertiesItems
            .map { it.type.resolve().declaration.asClassDeclaration() }
            .toList()

        writeToFile(environment.codeGenerator, collectedMap)

        return resolver.getClassDeclarationByName(className)
            ?.let { listOf(it) }
            ?: emptyList()
    }

    @OptIn(KspExperimental::class)
    private fun writeToFile(
        codeGenerator: CodeGenerator,
        collectedMap: List<KSClassDeclaration>
    ) {
        if (collectedMap.isEmpty()) return

        val destinations = collectedMap
            .filter { it.isAnnotationPresent(Destination::class) }

        val services = collectedMap
            .filter { it.isAnnotationPresent(KService::class) }

        val classSpec = TypeSpec.objectBuilder(className)
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

        // 将涉及到的类所涉及的文件作为依赖传入，方便增量编译
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