package com.test

import com.lalilu.krouter.InjectMap
import com.lalilu.krouter.KRouter
import com.testlib.ConfigProvider
import com.testlib.LoggerService
import com.testlib.ProfileScreen
import com.testlib.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KRouter 端到端运行时测试（KMP JVM target）。
 *
 * ## 测试方式
 *
 * 在真实的 Gradle KMP 构建中，KSP 处理器先编译生成 [KRouterInjectMap]，
 * 然后这些测试验证生成的代码在实际运行时的行为。
 *
 * ## 与 kctfork 单元测试的区别
 *
 * | 维度 | [KRouterProcessorTest] | 本测试 |
 * |------|----------------------|--------|
 * | 环境 | kctfork 单轮 JVM 编译 | Gradle KMP 多轮构建 |
 * | 验证目标 | 生成代码的内容结构 | 生成代码的运行时行为 |
 * | 跨模块 | 不支持（单模块） | 支持（依赖 test-lib） |
 * | expect/actual | 不支持 | 支持 |
 *
 * ## 测试前置条件
 *
 * KSP 处理器必须在测试运行前执行完成，由 Gradle 任务依赖保证。
 * 所有 `@KService` 和 `@Destination` 的声明见 commonMain 源集的同类包。
 *
 * @see KRouterProcessorTest kctfork 生成逻辑单元测试
 */
class KRouterE2ETest {
    // ========================================================================
    // 1. 基础功能验证
    // ========================================================================

    /**
     * **意图**：验证 @KInject 生成的 actual fun 可调用并返回非空 InjectMap。
     *
     * **预期**：[kRouterInjectMap()] 返回非空 [InjectMap] 实例。
     *
     * **意义**：表明生成的 actual 方法和 KRouterInjectMap 都被正确编译。
     */
    @Test
    fun `kRouterInjectMap actual function exists and returns InjectMap`() {
        val injectMap = kRouterInjectMap()
        assertNotNull("kRouterInjectMap() should not return null", injectMap)
    }

    /**
     * **意图**：验证 @KService 收集以在运行时可用。
     *
     * **预期**：services 列表同时包含 object 单例 [AnalyticsService]
     *         和 class 实例 [UserService]，且 object 通过 === 验证是同一个引用。
     *
     * **意义**：保证 Service 的「单例直接引用，class 构造注入」策略正确。
     */
    @Test
    fun `services list contains KService instances`() {
        val injectMap = kRouterInjectMap()

        val services = injectMap.services
        assertTrue(
            "services should contain AnalyticsService",
            services.any { it is AnalyticsService },
        )
        assertTrue(
            "services should contain UserService",
            services.any { it is UserService },
        )
        assertTrue(
            "AnalyticsService should be the singleton object",
            services.any { it === AnalyticsService },
        )
    }

    /**
     * **意图**：验证 getMap 能正确解析路由并构造目标对象。
     *
     * **输入**：路由 /test/home，参数 id=abc123。
     *
     * **预期**：返回的 [HomeScreen] 实例 id 字段为 "abc123"。
     *
     * **意义**：验证「路由 → 参数解析 → 对象构造」全链路正确。
     */
    @Test
    fun `getMap returns correct route handler`() {
        val injectMap = kRouterInjectMap()
        val handler = injectMap.getMap("/test/home")

        assertNotNull("getMap should return a handler for known route", handler)
        val result = handler(mapOf("id" to "abc123"))
        assertTrue("Result should be HomeScreen", result is HomeScreen)
        assertEquals("abc123", (result as HomeScreen).id)
    }

    // ========================================================================
    // 2. 多路由
    // ========================================================================

    /**
     * **意图**：验证 @Destination(router = ["/test/detail", "/test/detail/alt"])
     *         两个路径都能独立解析到 [DetailScreen]。
     *
     * **预期**：两个 handler 均返回 DetailScreen，albumId 分别为各自传入的值。
     *
     * **意义**：多路由别名机制应该完整覆盖所有注册路径。
     */
    @Test
    fun `route with multiple paths both resolve to same class`() {
        val injectMap = kRouterInjectMap()

        val handler1 = injectMap.getMap("/test/detail")
        val handler2 = injectMap.getMap("/test/detail/alt")

        val result1 = handler1(mapOf("albumId" to "A"))
        val result2 = handler2(mapOf("albumId" to "B"))
        assertTrue(result1 is DetailScreen)
        assertTrue(result2 is DetailScreen)
        assertEquals("A", (result1 as DetailScreen).albumId)
        assertEquals("B", (result2 as DetailScreen).albumId)
    }

    // ========================================================================
    // 3. 可选参数与默认值
    // ========================================================================

    /**
     * **意图**：验证可选参数（有默认值）在不提供时的 fallback 行为。
     *
     * **输入**：只提供必填 albumId，不提供 title 和 count。
     *
     * **预期**：title 为 null（默认值），count 为 0（默认值）。
     *
     * **意义**：默认值应被正确应用，不因参数缺失而崩溃。
     */
    @Test
    fun `route with optional nullable params`() {
        val injectMap = kRouterInjectMap()
        val handler = injectMap.getMap("/test/detail")

        val result = handler(mapOf("albumId" to "A123"))
        val screen = result as DetailScreen
        assertEquals("A123", screen.albumId)
        assertNull("title should be null (default)", screen.title)
        assertEquals("count should be 0 (default)", 0, screen.count)
    }

    // ========================================================================
    // 4. 泛型参数
    // ========================================================================

    /**
     * **意图**：验证 List<String>、Map<String, String> 等泛型类型的正确传递。
     *
     * **输入**：tags 为字符串列表，meta 为字符串映射。
     *
     * **预期**：构造的 [GenericScreen] 中各字段类型和内容匹配输入。
     *
     * **意义**：泛型参数的运行时类型擦除不应影响参数注入的正确性。
     */
    @Test
    fun `route with generic typed params`() {
        val injectMap = kRouterInjectMap()
        val handler = injectMap.getMap("/test/generic")

        val result =
            handler(
                mapOf(
                    "id" to "G1",
                    "tags" to listOf("tag1", "tag2"),
                    "meta" to mapOf("key" to "value"),
                ),
            )
        val screen = result as GenericScreen
        assertEquals("G1", screen.id)
        assertEquals(listOf("tag1", "tag2"), screen.tags)
        assertEquals(mapOf("key" to "value"), screen.meta)
    }

    // ========================================================================
    // 5. KRouter 完整链路
    // ========================================================================

    /**
     * **意图**：验证 KRouter.init + KRouter.route 全链路工作。
     *
     * **输入**：URL 式字符串 "/test/home?id=hello_world"。
     *
     * **预期**：@KRouter.route 返回 [HomeScreen]，id 为 URL 参数中的值。
     *
     * **意义**：这是框架对外暴露的入口 API，必须保证端到端可用。
     */
    @Test
    fun `KRouter integration works end to end`() {
        val injectMap = kRouterInjectMap()
        KRouter.init { injectMap.getMap(it) }

        val result = KRouter.route<HomeScreen>("/test/home?id=hello_world")
        assertNotNull("KRouter.route should return HomeScreen", result)
        assertEquals("hello_world", result?.id)
    }

    /**
     * **意图**：验证 KRouter.route 的 extraParams 参数注入。
     *
     * **输入**：通过 extraParams 传入 albumId 和 title。
     *
     * **预期**：返回 [DetailScreen] 且字段值匹配 extraParams。
     *
     * **意义**：开发者可通过 extraParams 动态附加参数，覆盖 URL 参数。
     */
    @Test
    fun `extra params are passed through to the route`() {
        val injectMap = kRouterInjectMap()
        KRouter.init { injectMap.getMap(it) }

        val result =
            KRouter.route<DetailScreen>(
                router = "/test/detail",
                extraParams = mapOf("albumId" to "extra_album", "title" to "Extra Title"),
            )
        assertNotNull(result)
        assertEquals("extra_album", result?.albumId)
        assertEquals("Extra Title", result?.title)
    }

    /**
     * **意图**：验证未注册的路由抛 [IllegalArgumentException]。
     *
     * **输入**：不存在的路由路径 "/unknown/route"。
     *
     * **预期**：抛出 [IllegalArgumentException]。
     *
     * **意义**：未知路由应快速失败而非静默吞掉错误。
     */
    @Test(expected = IllegalArgumentException::class)
    fun `unknown route throws exception`() {
        val injectMap = kRouterInjectMap()
        KRouter.init { injectMap.getMap(it) }

        KRouter.route<String>("/unknown/route")
    }

    // ========================================================================
    // 6. 跨模块收集
    //   验证 KSP 能跨 Gradle 模块收集 @Destination / @KService。
    // ========================================================================

    /**
     * **意图**：验证来自 test-lib 模块的 @Destination 能被主模块正确收集。
     *
     * **输入**：路由 /lib/profile，参数 userId=user_001。
     *
     * **预期**：返回 [ProfileScreen]（定义在 test-lib）。
     *
     * **意义**：跨模块路由收集是 KRouter 支持组件化架构的基础。
     */
    @Test
    fun `cross module destination is collected and routes resolve`() {
        val injectMap = kRouterInjectMap()
        val handler = injectMap.getMap("/lib/profile")

        val result = handler(mapOf("userId" to "user_001"))
        assertTrue("Result should be ProfileScreen from test-lib", result is ProfileScreen)
        assertEquals("user_001", (result as ProfileScreen).userId)
    }

    /**
     * **意图**：验证跨模块多路由绑定。
     *
     * **输入**：路由 /lib/settings 和 /lib/prefs（均指向 [SettingsScreen]）。
     *
     * **预期**：两条路径均返回 [SettingsScreen]，theme 均为默认值 "light"。
     *
     * **意义**：多路由别名在跨模块场景下也应完整工作。
     */
    @Test
    fun `cross module multi-route destinations both resolve`() {
        val injectMap = kRouterInjectMap()

        val handler1 = injectMap.getMap("/lib/settings")
        val handler2 = injectMap.getMap("/lib/prefs")

        val result1 = handler1(mapOf())
        val result2 = handler2(mapOf())

        assertTrue("Result should be SettingsScreen", result1 is SettingsScreen)
        assertTrue("Both routes should resolve to same type", result2 is SettingsScreen)
        assertEquals("light", (result1 as SettingsScreen).theme)
        assertEquals("light", (result2 as SettingsScreen).theme)
    }

    /**
     * **意图**：验证来自 test-lib 模块的 @KService 能被收集。
     *
     * **预期**：services 列表包含 [ConfigProvider] 和 [LoggerService]。
     *
     * **意义**：服务收集应与路由收集一样支持跨模块。
     */
    @Test
    fun `cross module KService is collected`() {
        val injectMap = kRouterInjectMap()

        val services = injectMap.services
        assertTrue(
            "Should contain ConfigProvider from test-lib",
            services.any { it is ConfigProvider },
        )
        assertTrue(
            "Should contain LoggerService from test-lib",
            services.any { it is LoggerService },
        )
    }
}
