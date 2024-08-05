package com.zhangke.krouter.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.zhangke.krouter.plugin.kcp.KCPComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

private const val TestScreen = """
package com.zhangke.krouter.test

import com.zhangke.krouter.annotation.Destination

interface Screen
interface TabScreen

@Destination("screen/test")
class TestScreen(
    val name: String = "testValue",
    val title: String? = null,
): Screen

@Destination("screen/main")
class MainScreen: Screen

@Destination("screen/settings")
class SettingsScreen: TabScreen
"""

@OptIn(ExperimentalCompilerApi::class)
class KRouterKCPPluginTest {

    @Test
    fun test() {
        val kotlinSource = listOf(
            SourceFile.kotlin("Screens.kt", TestScreen),
        )

        val result = compile(
            sourceFiles = kotlinSource,
            plugins = listOf(KCPComponentRegistrar())
        )
    }

    @OptIn(ExperimentalCompilerApi::class)
    fun compile(
        sourceFiles: List<SourceFile>,
        plugins: List<CompilerPluginRegistrar> = emptyList()
    ): JvmCompilationResult {
        return KotlinCompilation().apply {
            sources = sourceFiles
            inheritClassPath = true
            compilerPluginRegistrars = plugins
        }.compile()
    }
}

