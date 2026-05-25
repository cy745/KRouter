# KRouter 代码审查待办清单

> 从 0.0.5 版代码审查中记录，后续按优先级依次修复。

---

## P0 — 必须修复

### 1. `runCatching` 吞异常

**文件：** `compiler/.../KRouterInjectProcessor.kt:81-86`

`runCatching` 包裹了 `fileSpec.writeTo()`，静默吞掉了所有异常（含不可恢复异常）。需要至少记录错误日志。

**修复方案：**
```kotlin
runCatching {
    fileSpec.writeTo(...)
}.onFailure { e ->
    environment.logger.error("Failed to generate KRouterInjectMap: ${e.message}", e)
}
```

### 2. `routeParamsNameCache` 跨轮次污染

**文件：** `compiler/.../code/RouterMapKSP.kt:271-292`

`routeParamsNameCache` 是顶层 `mutableMapOf`，在 KSP 增量编译/多轮处理中会跨轮次缓存旧的参数名映射，导致错误复用。

**修复方案：** 将缓存改为 per-instance 的类成员变量，或在每次 process 调用时清理。

### 3. `afterEvaluate` / `beforeEvaluate` 反模式

**文件：** `plugin/.../RouterPlugin.kt:21-67`

Plugin 所有逻辑在 `afterEvaluate` 中执行，与 Gradle 9 的 configuration cache 不兼容。`beforeEvaluate` 的嵌套调用在跨项目场景下时序不可靠。

**修复方案：** 使用 `projectsEvaluated` 或 `TaskProvider` 惰性配置替代。

---

## P1 — 建议修复

### 4. 类名生成 `absoluteValue` 溢出

**文件：** `compiler/.../KRouterCollectProcessor.kt:86`

`Random.nextInt().absoluteValue` 在 `Int.MIN_VALUE` 时会溢出为负数。

**修复方案：** 改用 `Random.nextLong().absoluteValue` 或 `Random.nextString()`。

### 5. `@Param` 注解 URL 参数未 decode

**文件：** `core/.../KRouter.kt:18-30`

URL 风格参数（`screen?name=a%20b`）中的 `%20`、`+` 等编码未解码。参数值会包含原始编码字符串。

**修复方案：** 对 URL parse 后的 key 和 value 使用 `java.net.URLDecoder.decode()`。

### 6. KMP KSP Issue #567 引用已过时

**文件：** `plugin/.../RouterPlugin.kt:77`

KMP KSP 配置中引用了 2020-2021 年的 issue（#567），当前 KSP 2.3.7 很可能已修复该问题。

**修复方案：** 验证 issue 状态，简化 KMP 配置逻辑。

### 7. `KRouterProcessorProvider` 默认行为歧义

**文件：** `compiler/.../KRouterProcessorProvider.kt:18-22`

`kRouterType` 未指定时默认走 `collect` 模式，在 inject 模块上会导致只收集不注入。

**修复方案：** 要么抛异常要求明确指定，要么根据项目角色（根/子项目）自动推断。

---

## P2 — 代码整洁

### 8. 使用 `logger` 替代 `println`

**文件：** `plugin/.../RouterPlugin.kt:28,42`

两处使用了 `println` 而不是 Gradle 的 `logger.warn()`/`logger.info()`。

### 9. 清理注释掉的旧版本配置

**文件：** `gradle/libs.versions.toml:2-3`

注释掉的 `kotlin_version = "2.0.0"` 和 `ksp_version = "2.0.0-1.0.24"` 历史版本可以清理。

### 10. `kotlin-android` 插件声明不必要

**文件：** `gradle/libs.versions.toml:13`

`kotlin = { id = "org.jetbrains.kotlin.android" }` — 项目无 Android 模块，应改用 `org.jetbrains.kotlin.jvm` 或移除。

### 11. 参数组合指数爆炸风险

**文件：** `compiler/.../code/RouterMapKSP.kt:137-140`

`combinations()` 对可选参数生成 2^n 个 when 分支，10 个可选参数就生成 1024 个分支。

**修复方案：** 文档限制可选参数数量，或改用 builder 模式生成。

### 12. 代码生成字符串拼接代替 CodeBlock

**文件：** `compiler/.../code/RouterMapKSP.kt:251-264`

使用 `.replace(".value", "?.value")` 和 `.replace(" as ", " as? ")` 字符串操作拼接代码模板。

**修复方案：** 用 KotlinPoet 的 `CodeBlock` 条件分支直接生成两套类型安全模板。
