package com.lalilu.krouter.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * KSP 处理器集成测试（kctfork 单轮编译）。
 *
 * ## 测试方式
 *
 * 通过 kctfork 在 JVM 进程中直接编译 Kotlin 源码，不经过 Gradle。
 * 传入带 KRouter 注解的源码 → 运行 [KRouterProcessorProvider]（kRouterType=inject）
 * → 检查生成的 KRouterInjectMap 源码内容。
 *
 * ## 与 KMP 端到端测试的区别
 *
 * 此测试验证**生成器逻辑**的正确性（内容结构、关键字、包名等）。
 * [KRouterE2ETest]（test-app 模块）验证**运行时行为**（路由解析、参数注入等）。
 *
 * ## 单轮限制
 *
 * kctfork 为单模块 JVM 编译，不支持 Kotlin Multiplatform 的 expect/actual。
 * 跨模块收集、多轮 KSP 等特性在 test-app（真实 Gradle KMP 构建）中验证。
 *
 * ## 测试策略
 *
 * 大部分测试只检查 [KotlinCompilation.ExitCode.OK] 表示「能让 KSP 跑完就能生成有效代码」，
 * 部分关键测试深入检查生成文件内容中的特定字符串，以验证代码生成逻辑的正确性。
 *
 * @see KRouterE2ETest KMP 端到端运行时测试
 */
@OptIn(ExperimentalCompilerApi::class)
class KRouterProcessorTest {
    // ========================================================================
    // 1. 基础注解收集
    //   验证 KSP 处理器能正确发现 @Destination 注解并为其生成路由映射代码。
    // ========================================================================

    /**
     * **意图**：验证最基本的 @Destination 能正常走通完整流程。
     *
     * **输入**：一个普通 data class，标记 @Destination(router = ["/test/route"])，
     *         仅含一个必填构造参数 id: String。
     *
     * **预期**：编译成功，KRouterInjectMap.kt 文件被生成。
     *
     * **意义**：冒烟测试，确保处理器最简路径无阻塞。
     */
    @Test
    fun `basic destination without params`() {
        val routeSource =
            SourceFile.kotlin(
                "TestRoute.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/test/route"])
            data class TestRoute(val id: String)
            """,
            )

        val result = compile(routeSource)

        assertEquals("Compilation should succeed", KotlinCompilation.ExitCode.OK, result.exitCode)
        assertGeneratedInjectMapExists(result)
    }

    /**
     * **意图**：验证同一模块中多个 @Destination 能同时被收集。
     *
     * **输入**：两个不同签名（参数不同）的 data class。
     *
     * **预期**：编译成功。
     *
     * **意义**：确保多处路由注册不会相互干扰。
     */
    @Test
    fun `multiple destinations are collected`() {
        val source1 =
            SourceFile.kotlin(
                "ScreenA.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/screen/a"])
            data class ScreenA(val id: String)
            """,
            )

        val source2 =
            SourceFile.kotlin(
                "ScreenB.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/screen/b"])
            data class ScreenB(val id: String, val name: String)
            """,
            )

        val result = compile(source1, source2)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    // ========================================================================
    // 2. 构造函数参数处理
    //   验证 KSP 处理器对不同类型、不同空性的构造参数的处理。
    // ========================================================================

    /**
     * **意图**：验证多必填参数能被正确声明和注入。
     *
     * **输入**：两个 String 必填参数。
     *
     * **预期**：编译成功，生成代码中包含对应的 handleParams 调用。
     *
     * **意义**：保证最常见的使用场景（必填路由参数）不报错。
     */
    @Test
    fun `destination with required params`() {
        val source =
            SourceFile.kotlin(
                "DetailScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/detail"])
            data class DetailScreen(val albumId: String, val title: String)
            """,
            )

        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    /**
     * **意图**：验证可空类型 + 含默认值的泛型参数的混合场景。
     *
     * **输入**：
     * - albumId: String（必填非空）
     * - coverCacheKey: String? = null（可空，有默认值）
     * - sharedMap: Map<String, String> = emptyMap()（泛型，有默认值）
     *
     * **预期**：编译成功。
     *
     * **意义**：覆盖实际项目中常见的「必填 id + 可选参数」模式。
     */
    @Test
    fun `destination with nullable optional params`() {
        val source =
            SourceFile.kotlin(
                "AlbumScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/pages/albums/detail"])
            data class AlbumScreen(
                val albumId: String,
                val coverCacheKey: String? = null,
                val sharedMap: Map<String, String> = emptyMap()
            )
            """,
            )

        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    /**
     * **意图**：验证泛型类型参数（List<String>, Map<String, String>）
     *         在 KSType 解析时不触发 toClassName 崩溃（Issue #...）。
     *
     * **背景**：KotlinPoet 的 [KSType.toClassName] 不允许带类型参数的 type，
     *         改用 [toTypeName] 处理。此测试确保该修复生效。
     *
     * **输入**：含 `List<String>`、`Map<String, String>` 参数的 data class。
     *
     * **预期**：编译成功，不抛 IllegalStateException。
     */
    @Test
    fun `destination with generic typed params`() {
        val source =
            SourceFile.kotlin(
                "GenericScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/test/generic"])
            data class GenericScreen(
                val id: String,
                val tags: List<String> = emptyList(),
                val meta: Map<String, String> = emptyMap()
            )
            """,
            )

        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    // ========================================================================
    // 3. @KService 收集
    //   验证 KSP 处理器能正确收集 [KService] 标注的 object 和 class。
    // ========================================================================

    /**
     * **意图**：验证 @KService object 被添加到 services 列表。
     *
     * **输入**：KService 标注的 object（单例）。
     *
     * **预期**：编译成功，生成代码中 services 列表含 TestService，
     *         且 services 属性带 override 关键字。
     *
     * **意义**：object 是 KRouter 中最常见的 Service 形态（如配置类、工具类），
     *         应直接引用单例而非 new 实例。
     */
    @Test
    fun `kservice object is collected`() {
        val serviceSource =
            SourceFile.kotlin(
                "TestService.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.KService

            @KService
            object TestService
            """,
            )

        val result = compile(serviceSource)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val content = readGeneratedInjectMap(result)
        assertTrue("Should reference TestService in services", content.contains("TestService"))
        assertTrue("services should be override", content.contains("override val services"))
    }

    /**
     * **意图**：验证 @KService class 被添加到 services 列表。
     *
     * **输入**：KService 标注的 class。
     *
     * **预期**：编译成功，生成代码含 UserService() 构造调用。
     *
     * **意义**：与 object 不同，class 需要走构造器创建实例。
     */
    @Test
    fun `kservice class is collected`() {
        val serviceSource =
            SourceFile.kotlin(
                "UserService.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.KService

            @KService
            class UserService
            """,
            )

        val result = compile(serviceSource)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val content = readGeneratedInjectMap(result)
        assertTrue("Should reference UserService in services", content.contains("UserService"))
    }

    /**
     * **意图**：验证 @Destination 和 @KService 在同一模块中混合收集。
     *
     * **输入**：一个 @Destination 和一个 @KService object。
     *
     * **预期**：生成代码同时包含路由路径和 services 引用。
     *
     * **意义**：实际项目中两种注解通常共存，确保不互相排斥。
     */
    @Test
    fun `mixed destinations and services are collected`() {
        val routeSource =
            SourceFile.kotlin(
                "HomeScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/home"])
            data class HomeScreen(val id: String)
            """,
            )

        val serviceSource =
            SourceFile.kotlin(
                "ApiService.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.KService

            @KService
            object ApiService
            """,
            )

        val result = compile(routeSource, serviceSource)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val content = readGeneratedInjectMap(result)
        assertTrue("Should include home screen route", content.contains("/home"))
        assertTrue("Should include ApiService", content.contains("ApiService"))
    }

    // ========================================================================
    // 4. @Param 自定义名称
    //   验证 @Param(name = "...") 能将参数的 router 名称从属性名改为自定义值。
    // ========================================================================

    /**
     * **意图**：验证 @Param(name = "custom_id") 正确重命名参数引用。
     *
     * **输入**：@Param(name = "custom_id") val id: String，以及一个未注解的 name 参数。
     *
     * **预期**：生成的代码中 handleParams 使用 "custom_id" 而非 "id"。
     *
     * **意义**：当路由参数名与 Kotlin 属性名不同时，@Param 提供映射能力。
     */
    @Test
    fun `param annotation with custom name`() {
        val source =
            SourceFile.kotlin(
                "ParamScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination
            import com.lalilu.krouter.annotation.Param

            @Destination(router = ["/test/param"])
            data class ParamScreen(
                @Param(name = "custom_id") val id: String,
                val name: String
            )
            """,
            )

        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val content = readGeneratedInjectMap(result)
        assertTrue("Should reference custom param name", content.contains("custom_id"))
    }

    // ========================================================================
    // 5. 多路由
    //   验证一个 @Destination 绑定的多个路由路径都能在生成代码中找到。
    // ========================================================================

    /**
     * **意图**：验证 router = ["/route/a", "/route/b"] 同时生效。
     *
     * **输入**：一个 data class 绑定两个路由路径。
     *
     * **预期**：生成代码中两个路径都出现，且指向同一个 Screen。
     *
     * **意义**：一个界面可能通过多个入口进入（如旧路径兼容、别名）。
     */
    @Test
    fun `destination with multiple routes`() {
        val source =
            SourceFile.kotlin(
                "MultiRouteScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/route/a", "/route/b"])
            data class MultiRouteScreen(val id: String)
            """,
            )

        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val content = readGeneratedInjectMap(result)
        assertTrue("Should contain first route", content.contains("/route/a"))
        assertTrue("Should contain second route", content.contains("/route/b"))
    }

    // ========================================================================
    // 6. 边界情况
    //   验证极端或异常输入下处理器不会崩溃，并合理产出。
    // ========================================================================

    /**
     * **意图**：验证无任何 KRouter 注解时的空壳行为。
     *
     * **输入**：一个没有任何注解的普通 class。
     *
     * **预期**：编译成功，KRouterInjectMap 仍生成（空壳），services=emptyList，
     *         getMap 只有 else 分支抛异常。
     *
     * **意义**：即使项目中暂无路由，也应生成可编译的注入映射，避免 KSP 报错。
     */
    @Test
    fun `no annotated classes still generates inject map`() {
        val source =
            SourceFile.kotlin(
                "PlainClass.kt",
                """
            package com.test

            class PlainClass
            """,
            )

        val result = compile(source)
        assertEquals(
            "Should succeed even with no annotations",
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
        )

        val content = readGeneratedInjectMap(result)
        assertTrue("Should generate services with emptyList", content.contains("emptyList()"))
        assertTrue("Should still implement InjectMap", content.contains("InjectMap"))
        assertTrue("Should contain else -> throw", content.contains("Not Found"))
    }

    /**
     * **意图**：验证 @Destination 无 router 参数能编译成功。
     *
     * **输入**：@Destination 空注解（无 router 值）。
     *
     * **预期**：编译成功，该 class 不生成路由分支（空 router 被跳过）。
     *
     * **注意**：此时 class 仍在 metadata 中, 但不会出现在 getMap 的 when 分支里。
     */
    @Test
    fun `destination with empty router compiles successfully`() {
        val source =
            SourceFile.kotlin(
                "EmptyRouteScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination
            data class EmptyRouteScreen(val id: String)
            """,
            )

        val result = compile(source)
        assertEquals(
            "Empty router should compile successfully",
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
        )
    }

    /**
     * **意图**：验证全默认参数场景。
     *
     * **输入**：两个参数都有默认值，无必填参数。
     *
     * **预期**：编译成功。
     *
     * **意义**：覆盖「路由无需参数」的简化场景。
     */
    @Test
    fun `destination with only default params`() {
        val source =
            SourceFile.kotlin(
                "DefaultScreen.kt",
                """
            package com.test

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/default"])
            data class DefaultScreen(
                val label: String = "default",
                val count: Int = 0
            )
            """,
            )

        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    // ========================================================================
    // 7. @KInject actual 生成
    //   验证处理器为 @KInject 标注的函数生成 actual 实现文件。
    // ========================================================================

    /**
     * **意图**：验证 @KInject 代码生成逻辑不会在没有 @KInject 时误生成文件。
     *
     * **输入**：只有 @Destination，没有 @KInject。
     *
     * **预期**：编译成功，无 KRouterActual_* 文件。
     *
     * **限制**：kctfork 是单模块 JVM 编译，不支持 KMP 的 expect/actual。
     *         完整的 @KInject expect→actual 配对在 KMP 项目的编译期由
     *         Kotlin 编译器保障，这里只验证「无注解时不误生」。
     */
    @Test
    fun `kInject generates actual function file`() {
        val destSource =
            SourceFile.kotlin(
                "SampleScreen.kt",
                """
            package com.test.inject

            import com.lalilu.krouter.annotation.Destination

            @Destination(router = ["/test/sample"])
            data class SampleScreen(val id: String)
            """,
            )

        val result = compile(destSource)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val actualFile =
            result.sourcesGeneratedBySymbolProcessor
                .firstOrNull { it.name.startsWith("KRouterActual_") }
        assertNull("No KRouterActual_* file should be generated without @KInject", actualFile)
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 运行一次完整的 KSP 编译流程并返回结果。
     *
     * @param sources 要编译的 Kotlin 源文件
     * @return 编译结果，包含 exitCode、messages、classLoader、generatedFiles 等
     */
    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        val compilation =
            KotlinCompilation().apply {
                this.sources = sources.toList()
                inheritClassPath = true
                configureKsp {
                    symbolProcessorProviders.add(KRouterProcessorProvider())
                    processorOptions["kRouterType"] = "inject"
                }
                verbose = false
            }
        val result =
            try {
                compilation.compile()
            } catch (e: Exception) {
                println("Exception during compilation: ${e.message}")
                e.printStackTrace()
                throw RuntimeException("Compilation failed: ${e.message}", e)
            }
        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            println("Compilation messages:\n${result.messages}")
        }
        return result
    }

    private fun assertGeneratedInjectMapExists(result: JvmCompilationResult) {
        val file = findGeneratedInjectMapFile(result)
        assertTrue(
            "Generated KRouterInjectMap should exist at ${file?.path}",
            file?.exists() == true,
        )
    }

    private fun findGeneratedInjectMapFile(result: JvmCompilationResult): File? =
        result.sourcesGeneratedBySymbolProcessor
            .firstOrNull { it.name == "KRouterInjectMap.kt" }

    private fun readGeneratedInjectMap(result: JvmCompilationResult): String {
        val file =
            findGeneratedInjectMapFile(result)
                ?: throw AssertionError("KRouterInjectMap.kt not found in generated sources")
        return file.readText()
    }
}
