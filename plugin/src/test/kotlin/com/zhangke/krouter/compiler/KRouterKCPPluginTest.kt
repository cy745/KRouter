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
import kotlin.reflect.KClass

interface Screen
interface TabScreen

@Destination("screen/test")
data class TestScreen(
    val name: String = "testValue",
    val title: String? = null,
    val count: Int = 250,
    val isEnable: KClass<*> = Screen::class,
): Screen

@Destination("screen/main")
class MainScreen: Screen

@Destination("screen/settings")
class SettingsScreen: TabScreen
"""

private const val ValueKeeper = """
package com.zhangke.krouter

/**
 * 单纯用来存储KCP收集到的参数默认值
 */
object KRouterDefaultValueKeeper {
    val keeperMap: MutableMap<String, Any?> = mutableMapOf()

    fun get(name: String): Any? = keeperMap[name]
}
"""

@OptIn(ExperimentalCompilerApi::class)
class KRouterKCPPluginTest {

    @Test
    fun test() {
        val kotlinSource = listOf(
            SourceFile.kotlin("Screens.kt", TestScreen),
            SourceFile.kotlin("ValueKeeper.kt", ValueKeeper)
        )

        val result = compile(
            sourceFiles = kotlinSource,
            plugins = listOf(KCPComponentRegistrar())
        )

        val clazz = result.classLoader
            .loadClass("com.zhangke.krouter.KRouterDefaultValueKeeper")

        val ins = clazz.getField("INSTANCE")
            .get(null)

        val method = clazz.getDeclaredMethod("get", String::class.java)

        val defaultValue = method.invoke(ins, "com.zhangke.krouter.test.TestScreen\$count")
        println("count: $defaultValue")
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

