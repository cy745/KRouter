package com.lalilu.krouter.plugin

import com.google.devtools.ksp.gradle.KspExtension
import com.lalilu.krouter.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

class RouterPlugin : Plugin<Project> {
    companion object {
        const val KSP_ID = "com.google.devtools.ksp"
        const val COMPILER_NOTATION =
            "${BuildConfig.pluginGroup}:compiler:${BuildConfig.pluginVersion}"
    }

    override fun apply(target: Project) {
        target.afterEvaluate { _ ->
            val targetInjectProjectName =
                target.extensions.extraProperties
                    .runCatching { get("targetInjectProjectName") }
                    .getOrNull()

            // 若不存在则直接返回
            if (targetInjectProjectName == null) {
                println("Target inject project name not found")
                return@afterEvaluate
            }

            val isInjectProject: (Project) -> Boolean = {
                it.name == targetInjectProjectName
            }

            // 获取需要注入的project
            val targetInjectProject =
                target.takeIf(isInjectProject)
                    ?: target.subprojects.firstOrNull(isInjectProject)

            // 若不存在则直接返回
            if (targetInjectProject == null) {
                println("Target inject project not found")
                return@afterEvaluate
            }

            target.logger.info("KRouter Applied to ${targetInjectProject.name}")

            // 为目标项目添加KSP插件
            targetInjectProject.plugins.apply(KSP_ID)

            // 为目标项目的ksp配置处理器类型
            targetInjectProject.beforeEvaluate { pro ->
                pro.extensions
                    .getByType(KspExtension::class.java)
                    .arg("kRouterType", "inject")
            }

            // 为目标项目的依赖配置ksp
            targetInjectProject.afterEvaluate { project ->
                setUpKSP(project)

                goThroughProjectDependency(
                    root = project,
                    doInject = { project != it },
                )
            }
        }
    }
}

fun setUpKSP(project: Project) {
    val isKmpProject =
        runCatching {
            project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        }.getOrNull() != null

    if (isKmpProject) {
        val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kmpExtension.kspDependenciesForAllTargets {
            ksp(RouterPlugin.COMPILER_NOTATION)
        }
        project.kotlinExtension.sourceSets.getByName("commonMain").kotlin {
            srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    } else {
        project.dependencies.add("ksp", RouterPlugin.COMPILER_NOTATION)
    }
}

fun goThroughProjectDependency(
    root: Project,
    doInject: (project: Project) -> Boolean = { true },
) {
    if (doInject(root)) {
        root.plugins.apply(RouterPlugin.KSP_ID)
        root.beforeEvaluate {
            it.extensions
                .getByType(KspExtension::class.java)
                .arg("kRouterType", "collect")
        }
        runCatching { root.afterEvaluate { setUpKSP(project = root) } }
    }

    val dependencyProjects =
        root.configurations
            .flatMap { it.allDependencies }
            .filterIsInstance<ProjectDependency>()
            .mapNotNull { dep -> root.rootProject.findProject(dep.path) }
            .filter { it != root }
            .takeIf { it.isNotEmpty() }
            ?: return

    dependencyProjects.forEach {
        goThroughProjectDependency(
            root = it,
            doInject = doInject,
        )
    }
}
