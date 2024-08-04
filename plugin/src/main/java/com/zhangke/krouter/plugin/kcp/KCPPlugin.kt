package com.zhangke.krouter.plugin.kcp

import com.zhangke.krouter.plugin.BuildConfig
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KCPPlugin : KotlinCompilerPluginSupportPlugin {
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {

        println("KCPPlugin: hello")

        val project = kotlinCompilation.target.project
        return project.provider { emptyList() }
    }

    override fun getCompilerPluginId(): String {
        return "com.zhangke.krouter"
    }

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.zhangke.krouter",
        artifactId = "plugin",
        version = BuildConfig.pluginVersion,
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return true
    }
}