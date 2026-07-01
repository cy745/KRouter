package com.lalilu.kspcollector

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * 注解收集器——扫描指定注解，生成 metadata 类到共享包。
 *
 * 每个收集到的类以 `lateinit var` 属性的形式记录在 metadata 类中。
 * metadata 类名基于已收集类的全限定名列表的 hash，内容变化时类名自动变更，
 * 支持增量编译。
 *
 * 使用示例：
 * ```kotlin
 * val collector = MetadataCollector(
 *     CollectorConfig(
 *         annotations = listOf("com.example.MyAnnotation"),
 *         sharedPackage = "com.example.generated",
 *     ),
 * )
 *
 * val metadataName = collector.collect(resolver, codeGenerator)
 * if (metadataName != null) {
 *     // 返回 metadata 类 -> KSP 下一轮可见
 *     return listOf(resolver.getClassDeclarationByName(metadataName)!!)
 * }
 * ```
 */
class MetadataCollector(
    private val config: CollectorConfig,
) {
    /**
     * 执行一轮收集：
     * 1. 扫描 [config.annotations] 标记的类
     * 2. 将结果写入一个 metadata 类到 [config.sharedPackage]
     *
     * @return metadata 类的完整名（不含包），若无注解则返回 null
     */
    fun collect(
        resolver: Resolver,
        codeGenerator: CodeGenerator,
    ): String? {
        val collected =
            config.annotations.flatMap { annotationName ->
                resolver
                    .getSymbolsWithAnnotation(annotationName)
                    .map { it as KSClassDeclaration }
                    .toList()
            }

        if (collected.isEmpty()) return null

        val propertySpecs =
            collected.mapNotNull { item ->
                val name =
                    item.qualifiedName
                        ?.asString()
                        ?.replace('.', '_')
                        ?: return@mapNotNull null

                PropertySpec
                    .builder(name, item.toClassName())
                    .addModifiers(KModifier.LATEINIT, KModifier.PRIVATE)
                    .mutable(true)
                    .build()
            }

        val hash =
            collected
                .mapNotNull { it.qualifiedName?.asString() }
                .sorted()
                .joinToString("|")
                .hashCode()
                .toUInt()
                .toString(16)

        val className = "Metadata_${config.annotations.hashCode().toUInt().toString(16)}_$hash"
        val classSpec =
            TypeSpec
                .classBuilder(className)
                .addKdoc(config.kdoc)
                .addProperties(propertySpecs)
                .build()

        val fileSpec =
            FileSpec
                .builder(config.sharedPackage, className)
                .addType(classSpec)
                .indent("    ")
                .build()

        val dependencies =
            collected
                .mapNotNull { it.containingFile }
                .distinct()
                .toTypedArray()

        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            dependencies = Dependencies(aggregating = true, *dependencies),
        )

        return className
    }
}
