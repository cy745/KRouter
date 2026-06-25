package com.lalilu.krouter.plugin

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * copy from https://github.com/eygraber/gradle-conventions/blob/master/conventions-plugin/src/main/kotlin/ksp.kt
 */
interface KspDependencies {
    fun ksp(dependencyNotation: Any)
}

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

fun KotlinMultiplatformExtension.kspDependenciesForAllTargets(block: KspDependencies.() -> Unit) {
    targets.configureEach { target ->
        if (target.targetName != "metadata") {
            target.kspDependencies(block)
        }
    }
}
