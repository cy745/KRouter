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
package com.zhangke.krouter.test

import java.lang.IllegalArgumentException

private fun <T : Any> TOINJECT(): T {
    throw IllegalArgumentException("Not Injected.")
}

data class Screen2(
    val number: Int,
    val title: String = "test",
)

object RouterMap {
    val map: Map<String, (Map<String, Any>) -> Any?> = mapOf(
        "screen" to { params ->
            val number = params["number"] as? Int
                ?: throw IllegalArgumentException("No number param provided.")
            val title = params["title"] as? String
    
            Screen2(
                number = number,
                title = title ?: TOINJECT()
            )
        },
        "TestScreen" to { params ->
            val name = params["name"] as? String
    
            TestScreen(
                name = name ?: TOINJECT(),
            )
        }
    )
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

        // 反射获取经过处理后的类
        val clazz = result.classLoader.loadClass("com.zhangke.krouter.test.RouterMap")
        val ins = clazz.getField("INSTANCE").get(null)
        val method = clazz.getDeclaredMethod("getMap")
        val methodResult = method.invoke(ins) as Map<*, *>

        // 传参尝试调用方法，获取结果
        methodResult.forEach { entry ->
            val key = entry.key as? String
            val value = entry.value as? (Map<String, Any>) -> Any?
            val resultItem = value?.invoke(mapOf("name" to "123", "number" to 123))

            println("result: [$key] -> $resultItem")
        }
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

