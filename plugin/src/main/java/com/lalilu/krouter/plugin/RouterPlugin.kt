package com.lalilu.krouter.plugin

import com.google.devtools.ksp.gradle.KspExtension
import com.lalilu.krouter.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class RouterPlugin : Plugin<Project> {
    companion object {
        const val KSP_ID = "com.google.devtools.ksp"
        const val COMPILER_NOTATION =
            "${BuildConfig.pluginGroup}:compiler:${BuildConfig.pluginVersion}"
        const val EXTRA_ANNOTATIONS_PROP = "krouter.collect.annotations"
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

            val collectAnnotations =
                target.extensions.extraProperties
                    .runCatching { get(EXTRA_ANNOTATIONS_PROP) }
                    .getOrNull() as? String

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

            // 为目标项目的ksp配置处理器类型和扩展注解
            targetInjectProject.beforeEvaluate { pro ->
                val kspExt = pro.extensions.getByType(KspExtension::class.java)
                kspExt.arg("kRouterType", "inject")
                if (collectAnnotations != null) {
                    kspExt.arg(EXTRA_ANNOTATIONS_PROP, collectAnnotations)
                }
            }

            // 为目标项目的依赖配置ksp
            targetInjectProject.afterEvaluate { project ->
                setUpKSP(project)

                goThroughProjectDependency(
                    root = project,
                    doInject = { project != it },
                    collectAnnotations = collectAnnotations,
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
    } else {
        project.dependencies.add("ksp", RouterPlugin.COMPILER_NOTATION)
    }
}

fun goThroughProjectDependency(
    root: Project,
    doInject: (project: Project) -> Boolean = { true },
    collectAnnotations: String? = null,
) {
    if (doInject(root)) {
        root.plugins.apply(RouterPlugin.KSP_ID)
        root.beforeEvaluate {
            val kspExt = it.extensions.getByType(KspExtension::class.java)
            kspExt.arg("kRouterType", "collect")
            if (collectAnnotations != null) {
                kspExt.arg(RouterPlugin.EXTRA_ANNOTATIONS_PROP, collectAnnotations)
            }
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
            collectAnnotations = collectAnnotations,
        )
    }
}
