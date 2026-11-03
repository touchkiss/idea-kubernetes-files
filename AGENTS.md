# Repository Guidelines

## 项目结构与模块边界

本仓库是为原生 Kubernetes 扩展 Files 页签的 IntelliJ IDEA 插件，使用 Java 25，兼容范围限定为 IDEA 2026.2（`262.*`）。只提供原生集成，不注册独立文件浏览窗口。

- `src/main/java/io/github/kubernetesfiles/core/`：连接参数、子进程、目录读取、文件传输与校验；保持不依赖 IDEA。
- `integration/`（同一 Java 包根目录下）：官方 Kubernetes 插件的可选适配；非稳定 API 的改动必须重新验证。
- `ui/`：Swing 文件面板、设置和后台任务；原生 Files 页签适配和生命周期管理位于 `integration/`。
- `src/main/resources/META-INF/`：插件描述与可选依赖配置。
- `src/test/java/`：核心与 IDE 平台测试；`docs/`：设计、实施计划和验证记录。

## 构建、测试与本地开发

准备 JDK 25 和 IDEA 2026.2 安装，使用仓库自带 Gradle Wrapper：

```bash
./gradlew test
./gradlew buildPlugin
./gradlew verifyPluginProjectConfiguration
./gradlew runIde
```

以上依次运行测试、生成 `build/distributions/` 下的安装 ZIP、检查插件项目配置、启动隔离 IDE。默认 SDK 为 `/Applications/IntelliJ IDEA.app`；其他位置通过 `-PlocalIdePath='/path/to/IntelliJ IDEA'` 指定。`runIde` 使用 `.intellijPlatform/sandbox/`。

## 编码风格与命名

Java 使用四空格缩进，包名小写，类名采用 `PascalCase`，方法和变量采用 `camelCase`。沿用相邻代码风格，避免无关格式调整。构建脚本使用 Kotlin DSL；当前未配置独立格式化或 lint 工具。耗时传输放在后台，Swing 更新遵循事件分发线程规则。

## 测试规范

核心使用 JUnit 4，平台测试继承 `LightPlatformTestCase`。测试类命名为 `*Test`；核心方法使用描述行为的名称，平台测试方法以 `test` 开头。传输变更应覆盖二进制、特殊文件名、覆盖、取消、失败保留原件及校验异常。

测试需串行运行，避免多个 Gradle 测试进程共用 IDE 测试缓存。核心测试以本地 shell 模拟 kubectl，不代表真实集群验证。UI 渲染证据位于 `build/reports/ui/browser-panel.png`。当前没有量化覆盖率门槛；验证结果与未测边界记录到 `docs/verification.md`。

## 提交与 Pull Request

仓库尚未形成固定提交规范。使用简短、明确的祈使式摘要，例如 `Fix upload cancellation cleanup`。PR 应说明问题、行为变化、范围和验证命令；有关联 issue 时附链接，UI 变更附截图，并标明真实集群及原生容器选择联动是否验证。

## 安全与操作边界

不得提交 kubeconfig、凭据或本地构建缓存。每次 exec 显式绑定 context、namespace、Pod 和 container，不修改全局 current-context。路径经独立 shell 参数传递，二进制传输禁用 TTY。保留覆盖确认、同目录临时文件和 SHA-256 校验机制。未经明确授权，不操作真实集群、不上传业务 WAR、不重启工作负载，也不安装到日常 IDE。
