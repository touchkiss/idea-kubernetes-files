# Kubernetes Files Implementation Plan

目标：可安装 IDEA 插件，浏览容器目录并双向传输普通文件。
架构：core 的 Target/RemoteEntry/ProcessRunner/KubectlClient 独立于 IDE；ui 管理后台任务与交互；integration 适配原生容器 DataKey。

- [x] Task 1: core 与 CoreTest；先运行实际 shell 文件操作测试看到失败，再实现。验证 ./gradlew test。
- [x] Task 2: BrowserPanel/BrowserService/BrowserToolWindow/OpenBrowserAction；连接发现、导航、上传拖放、下载、覆盖确认、取消。验证编译与 sandbox。
- [x] Task 3: KubernetesSelection；Tools 和快捷键绑定原生选择准确 kubeconfig/context/container。核验 262 API。
- [x] Task 4: README/构建说明/兼容边界；test/buildPlugin/verifyPluginProjectConfiguration；独立复核并交付 ZIP。

不自动 commit/push，不写业务 Pod。用户已授权在新目录连续完成，inline 执行。

## 执行记录
- TDD：最初12项测试11项因未实现失败；实现后12项通过。
- 独立复核指出长文件名临时路径和mv目录竞态，增加回归并修复。
- 上传中途取消回归揭示快速退出时被误记为SHA失败；结束状态处理优先识别取消，避免挂起writer错误。
- 原生联动改为可选描述文件；sandbox默认禁用Ultimate，不更改用户授权/许可状态。通用面板仍可加载。
- 平台测试加载真实IDE插件服务、渲染面板；核心17项+IDE1项通过。
- 最终文档和兼容边界见 verification.md。
- 2026-11-03 后续：按用户要求复用有效本地授权运行 Ultimate 沙箱；增加 Services 原生右键菜单/工具栏入口，补齐 Pod 和多容器选择适配。25 项测试及构建通过，原生手工端到端边界见 verification.md。
- 2026-11-03 后续：用户确认原生右键菜单及连接成功，并要求嵌入右侧内容区。使用原生 `addConsoleTab()` 添加 Files 页签，按目标复用，关闭时释放资源；独立面板保留为手动连接与无原生内容上下文时的入口。
- 2026-11-03 后续（0.1.2）：容器选择改为原生风格的二级菜单；字节数与速度同步至 IDEA 后台任务进度。35 项测试、插件构建、配置与结构检查通过，手工验证边界见 verification.md。
- 2026-11-03 后续（0.1.3）：新增默认远程目录设置，留空时探测容器工作目录；区分自动回退与固定目录错误。42 项测试、构建及配置 / 结构检查通过，真实 Pod 的探测边界见 verification.md。
- 2026-11-03 后续（0.1.4）：按用户要求删除独立浏览窗口、手动连接入口及独立回退，仅保留原生 Files 页签。42 项测试及构建 / 配置 / 结构检查通过，并确认发行包不含旧窗口实现。
