package com.lalilu.krouter.plugin

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * KMP 项目中按 target 分别配置 KSP 的 DSL 工具。
 *
 * 使用示例：
 * ```kotlin
 * kotlin {
 *   jvm()
 *   iosArm64()
 * }
 *
 * // 为每个非 metadata target 添加 KSP 处理器依赖
 * kotlinExtension.kspDependenciesForAllTargets {
 *     ksp(project(":my-processor"))
 * }
 * ```
 *
 * @see [KotlinMultiplatformExtension.kspDependenciesForAllTargets]
 */

/** DSL 接收器：只有一个 [ksp] 方法用于添加依赖。 */
interface KspDependencies {
    fun ksp(dependencyNotation: Any)
}

/**
 * 为 [KotlinTarget] 添加 KSP 依赖。
 * 配置名称按 KSP 惯例自动拼接：`ksp` + TargetName（首字母大写）。
 * 例如 JVM target 对应 `kspJvm`，iOS Arm64 对应 `kspIosArm64`。
 */
fun KotlinTarget.kspDependencies(block: KspDependencies.() -> Unit) {
    val configurationName =
        "ksp${targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
    val dependencies: DependencyHandler = project.dependencies
    val kspDeps = object : KspDependencies {
        override fun ksp(dependencyNotation: Any) {
            dependencies.add(configurationName, dependencyNotation)
        }
    }
    kspDeps.block()
}

/**
 * 为 Kotlin Multiplatform 项目的所有 target（排除 metadata）添加 KSP 依赖。
 * 替代手动编写 `add("kspJvm", ...)`, `add("kspIosArm64", ...)` 等重复代码。
 */
fun KotlinMultiplatformExtension.kspDependenciesForAllTargets(block: KspDependencies.() -> Unit) {
    targets.configureEach { target ->
        if (target.targetName != "metadata") {
            target.kspDependencies(block)
        }
    }
}
