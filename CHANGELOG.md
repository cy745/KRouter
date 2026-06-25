# Changelog

## [0.0.7] — 2026-06-25

### Features
- 新增 `@KInject` 注解，支持 `expect fun kRouterInjectMap(): InjectMap` 模式，
  在 KMP 中通过 KSP 自动生成 `actual` 实现，桥接 commonMain 与 platform 源集
- 新增 `InjectMap` 接口（`val services` + `fun getMap`），`KRouterInjectMap`
  实现该接口，方便统一访问路由映射和服务列表
- 新增跨模块注解收集：`kRouterType=collect` 的模块生成 metadata 到
  `com.lalilu.krouter.generated`，`kRouterType=inject` 的主模块在第二轮
  合并读取本模块 + 所有依赖模块的 `@Destination` / `@KService`，实现多模块聚合
- KSP 配置工具 `KspConfig.kt`：为 KMP 项目逐个 target 配置 KSP 的 DSL

### Improvements
- Metadata 类名由随机数改为基于内容 hash，构建结果确定性，增量编译更精确
- Metadata 类从 `private` 改为 `public`，确保跨模块可见性
- `KRouterInjectProcessor` 三路分支设计（多轮 / 单轮 / 空壳），适配
  Gradle KMP 多轮构建与 kctfork 单轮测试两种场景
- `@KInject actual` 生成在第一轮完成（第二轮 KSP 中 `expect fun` 不可见），
  `KRouterInjectMap` 在第二轮生成（此时依赖模块 metadata 已索引）
- 第二轮合并策略：`getDeclarationsFromPackage()` 读跨模块 metadata +
  `getSymbolsWithAnnotation()` 补充当前模块自己的注解
- `buildRouterCondition` 增加空路由跳过逻辑，`@Destination(router = [])` 不再
  生成非法代码
- 统一所有生成文件头部为统一 KDoc 格式，含 GitHub 链接
- `KRouterInjectProcessor` 单轮回退路径已移除，始终走两轮流程
- `skipSigning` 属性支持本地发布（`-PskipSigning`）

### Infrastructure
- kctfork 测试框架从 `0.5.1` 升级到 `0.12.1`，适配 Kotlin 2.3.x
- 新增 ktlint 代码格式化工具（`org.jlleitschuh.gradle.ktlint:14.2.0`）
- 自动 pre-commit hook 运行 `ktlintFormat` 并重新 stage 格式化文件
- `.editorconfig` 基础风格配置，`build.gradle.kts` 排除在 ktlint 外
- GitHub Actions CI：每次 push / PR 自动编译 + 测试 + ktlint 检查
- GitHub Actions Publish：release 创建时自动发布到 Maven Central Portal
- Dokka 迁移助手警告通过 `gradle.properties` 静默

### Bug Fixes
- 修复 `KSType` 泛型参数（`List<String>`, `Map<String,String>`）调用
  `toClassName()` 崩溃，改用 `toTypeName()` 替代
- 修复 `handleParams<T>` 中可空类型生成语法错误（不再生成 `handleParams<LAlbum?>`）
- 修复 `@Destination(router = [])` 空路由生成非法 `when` 分支代码
- 修复 `@KInject actual` 缺少 `@KInject` 注解导致的编译警告（expect→actual
  注解一致性要求），同时通过 `func.isActual` 过滤防止循环生成
- 修复第二轮 `getDeclarationsFromPackage` 无法读取当前模块注解（同一轮
  `super.process()` 刚生成尚未索引），改用 `getSymbolsWithAnnotation` 补充

### Added Tests
- **compiler 单元测试**（kctfork `KRouterProcessorTest`）14 个测试：
  - `@Destination` 基础收集、多路由、必填/可空/泛型参数
  - `@KService` 收集（object / class / 混合）
  - `@Param` 自定义名称映射
  - 边界情况（无注解空壳、空 router、全默认参数）
  - `@KInject` 生成文件验证
- **KMP 端到端测试**（test-app `KRouterE2ETest`）12 个测试：
  - 本模块路由、多路由、参数注入、`List<String>` / `Map<String,String>` 泛型
  - 跨模块路由（`ProfileScreen`、`SettingsScreen`）
  - 跨模块服务（`ConfigProvider`、`LoggerService`）
  - `KRouter.init` + `KRouter.route` 完整链路
  - 未知路由异常

[0.0.7]: https://github.com/cy745/KRouter/compare/0.0.6...0.0.7
