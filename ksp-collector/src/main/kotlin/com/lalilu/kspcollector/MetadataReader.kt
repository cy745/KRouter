package com.lalilu.kspcollector

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Metadata 读取器——从共享包中读取所有已生成的 metadata 类，
 * 聚合其中引用的被收集类。
 *
 * 跨模块聚合原理：
 * ```
 * 依赖模块 A (collect phase)         当前模块 (read phase)
 *   Metadata_xxx {                     resolver.getDeclarationsFromPackage()
 *     lateinit var a: A                  → Metadata_xxx.properties
 *   }                                    → Metadata_yyy.properties
 * 依赖模块 B                               → 合并去重
 *   Metadata_yyy {                       → List<KSClassDeclaration>
 *     lateinit var b: B
 *   }
 * ```
 *
 * 使用示例：
 * ```kotlin
 * val reader = MetadataReader(sharedPackage = "com.example.generated")
 * val classes = reader.readCollectedClasses(resolver)
 * val routes = classes.filter { it.isAnnotationPresent(MyRoute::class) }
 * ```
 */
class MetadataReader(
    private val sharedPackage: String,
) {
    /**
     * 扫描 [sharedPackage] 下所有 metadata 类，提取它们引用的被收集类。
     *
     * @param resolver KSP 解析器
     * @return 去重后的被收集类列表
     */
    @OptIn(KspExperimental::class)
    fun readCollectedClasses(resolver: Resolver): List<KSClassDeclaration> {
        val metadataClasses =
            resolver
                .getDeclarationsFromPackage(sharedPackage)
                .filterIsInstance<KSClassDeclaration>()
                .toList()

        return metadataClasses
            .flatMap { it.getDeclaredProperties() }
            .map { it.type.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .distinct()
    }

    /**
     * 扫描 [sharedPackage] 下所有 metadata 类，并同时用 [resolver] 直接
     * 收集当前模块指定注解的类。两者合并去重后返回。
     *
     * 适用于在同一轮中既读取跨模块 metadata，又补充当前模块注解的场景。
     *
     * @param resolver KSP 解析器
     * @param ownAnnotations 当前模块要额外扫描的注解 FQN 列表
     * @return 去重后的被收集类列表
     */
    @OptIn(KspExperimental::class)
    fun readWithFallback(
        resolver: Resolver,
        ownAnnotations: List<String>,
    ): List<KSClassDeclaration> {
        val fromMetadata = readCollectedClasses(resolver)

        val fromOwn =
            ownAnnotations.flatMap { annotationName ->
                resolver
                    .getSymbolsWithAnnotation(annotationName)
                    .map { it as KSClassDeclaration }
                    .toList()
            }

        return (fromMetadata + fromOwn).distinct()
    }
}
