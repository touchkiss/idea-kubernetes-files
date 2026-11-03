# 验证记录（2026-11-03）

目标：本机 IntelliJ IDEA 2026.2.4 / IU-262.11879.100；构建JDK 25；插件0.1.0。

## 本地通过

命令：`./gradlew test buildPlugin verifyPluginProjectConfiguration`（JAVA_HOME 指向JDK25）。

- 17项core测试：目录文件名/类型、二进制往返和进度、空文件、上传/下载覆盖、目录与符号链接拒绝、失败下载保留原件、悬空链接、取消前及传输中取消、stderr错误、超时、连接字段、显式context/container与无TTY、长文件名、并发目标目录、上传损坏校验。
- 1项IDE平台测试：真实插件Project Service加载、Swing面板创建、双向按钮、Tools动作注册，渲染界面PNG。不是手工鼠标拖放测试。
- buildPlugin生成可安装ZIP。
- verifyPluginProjectConfiguration完成，保留1条until-build建议警告：由于原生适配依赖262插件API，有意限制262.*，没有宣称跨版本兼容。
- sandbox日志确认 `Loaded custom plugins: Kubernetes Files (0.1.0)`。
- 独立只读评审发现的两个P2问题已补回归并修复。

## 失败与处置

- 初次TDD预期失败：未实现行为；已实现并通过。
- 两次测试误并行共享了隔离system-test索引，后续出现索引损坏；只清理本项目可丢弃system-test缓存，串行重新执行后通过。未修改日常IDE缓存。
- 初次sandbox中Kubernetes依赖无法加载：sandbox禁用Ultimate。改为可选原生适配后通用面板加载；没有启用或绕过授权。
- 回归揭示的取消状态分类错误已修复。
- 清理sandbox时误终止一次最终测试进程（SIGTERM 143）；此后清理本项目隔离测试缓存并完整串行重跑，以重跑结果为最终证据。

## 证据边界与未运行

- 真实集群/Pod文件上传下载、RBAC、目标容器工具兼容性、真实断连清理：未运行，不把本地shell模拟当成集群验证。
- 原生Services选中容器后的快捷键端到端：未运行；本机262 API签名与编译通过，sandbox的Kubernetes模块因Ultimate禁用未激活。
- Finder拖放/系统文件选择器手工交互：未运行；平台测试确认面板及动作加载，核心传输协议已测。
- 跨IDE版本Plugin Verifier扫描：未运行；本项目的配置检查不等同完整二进制兼容扫描。
- 不安装到用户日常IDE，不上传业务WAR，不重启容器，不提交或推送代码。

core测试用本地sh模拟exec。macOS的mv/ln不支持Linux -T，测试使用明确的Linux语义适配器；GNU/BusyBox实际工具兼容性仍需目标容器验证。

## 本机授权复用验证（2026-11-03）

- Gradle 2.17.0 下为 `prepareSandbox` 增加可选本地授权同步；路径配置在仓库外的 Gradle 用户配置中，授权内容不进入仓库或日志。
- 使用 JDK 25 执行 `./gradlew prepareSandbox verifyPluginProjectConfiguration` 通过；仍保留上述有意限制 `262.*` 的兼容范围提示。
- 检查确认运行沙箱的授权副本与源文件一致，权限为 `0600`，Ultimate 模块不在禁用列表中。
- 实际执行 `./gradlew runIde`，本次启动日志确认 JetBrains Ultimate、Kubernetes 和 Kubernetes Files 均已加载，不再出现 Ultimate 被禁用的记录。
- 此验证仅确认沙箱启动与插件加载，未连接真实集群，也未验证原生容器选择后的端到端交互；无需据此扩大其他验证结论。

## 原生右键菜单与 Pod 选择适配（2026-11-03）

- 根因证据：旧适配只读取 `KUBERNETES_CONTAINER`，合成的单容器 Pod 事件返回空目标；新增回归先失败，修改后通过。快捷键未触发的具体原因未在用户现场复现，另补充现代 macOS Keymap 的显式绑定。
- 检查本机 262 SDK 的描述文件和字节码：原生 Pod / 容器动作列表动态生成，但其外层 `ServiceViewItemPopup`、`ServiceViewItemToolbar` 可追加动作；新入口为“浏览容器文件…”，按单选 Kubernetes Pod / 容器过滤。
- 单容器 Pod 生成唯一目标，多容器 Pod 返回候选并由 UI 选择；支持容器 DataKey、Services descriptor 及 Pod 资源上下文，不回退到其他 kubeconfig。
- JDK 25 执行 `./gradlew test buildPlugin verifyPluginProjectConfiguration` 成功：17 项核心测试、1 项面板测试、7 项原生选择适配测试，共 25 项，无失败。配置检查仍保留原有 `262.*` 范围提示。
- 新测试覆盖 Pod 精确连接字段、多容器不得静默选首项、无关 Services 节点隐藏、Pod 动作启用、多选拒绝、明确容器优先、非本地配置拒绝。这些使用合成原生事件和本地临时配置，不连接集群。
- 新版沙箱可启动，日志未见本插件动作注册错误；桌面工具能读取日常 IDEA，但无法选中以 Java 进程启动的沙箱，因此原生右键菜单手工点击、多容器选择框交互和自动打开远端目录仍未端到端验证。未将新版安装到日常 IDEA。

## 原生 Files 页签（0.1.1，2026-11-03）

- 用户反馈原生右键菜单及自动连接成功；这是用户现场反馈，不是自动测试覆盖。
- 修正先前对页签接入能力的判断：没有发现专用的第三方 Files 扩展点，但本机 `KubernetesResourceContent.addConsoleTab()` 可以添加同级内容。现通过 Pod / 容器 descriptor 获得原生内容对象，打开 `Files: 容器名`，而非默认激活独立工具窗口；缺少原生内容对象时保留回退。
- 文件面板增加嵌入模式，隐藏连接表单、保留重新连接按钮。按完整 Target 复用同节点页签，不同容器使用独立面板；Content disposer 释放面板，关闭原生内容或插件服务时清理文件页签。
- 5 项新增测试使用真实 `KubernetesResourceContent` 和 ContentManager，合成 API model 不允许联网，连接回调不执行 kubectl。验证与 Information / Shell 并列、选择 Files、目标复用、多容器隔离、关闭重建、原生内容关闭释放及插件清理保留 Shell。
- 已生成并目视检查 `build/reports/ui/native-files-tab.png`，确认实际原生页签组件布局；这是平台测试渲染，不是用户集群截图。
- JDK 25 执行 `./gradlew test buildPlugin verifyPluginProjectConfiguration` 成功，共 30 项测试、0 失败；生成 `build/distributions/kubernetes-files-0.1.1.zip`。保留原有 `262.*` 兼容范围提示；未运行跨版本 Plugin Verifier。
- 未连接真实 Pod 验证新版 Files 页签内传输，也未将 0.1.1 安装到日常 IDEA。内容 API 依赖 262 内部实现，升级须重新核验。

## 容器二级菜单与全局进度（0.1.2，2026-11-03）

- “浏览容器文件…”从普通动作改为动态 ActionGroup，保持原动作 ID 与快捷键。单容器为可直接执行的入口，多容器使用二级菜单；按本机原生 smart group 的 Presentation 标志处理单项、弹出组和快捷键。菜单生成只读取选择信息，不创建面板或发起 kubectl 请求。
- 去除多容器选择对话框；子动作绑定所选容器名，执行时重新校验该容器仍在当前选择中。
- 上传下载继续使用 IDEA `Task.Backgroundable`，显式从后台启动。提取 TransferProgress，把文件名、当前文件字节比例、字节数及速度同步到 ProgressIndicator，而非只更新面板；面板收起不影响更新。全局取消与面板取消都接入同一传输取消检查。
- 新增 2 项菜单测试和 3 项全局进度测试，覆盖二级菜单子项、选定容器绑定、单容器直接执行、无可见面板时进度上报、新文件进度重置以及两类取消入口。
- JDK 25 下 `./gradlew test buildPlugin verifyPluginProjectConfiguration` 与 `./gradlew verifyPluginStructure` 均成功，共 35 项测试、0 失败，生成 `build/distributions/kubernetes-files-0.1.2.zip`。保留原有 `262.*` 兼容提示。
- 未连接实际集群进行新版多容器菜单及收起面板传输的手工端到端验证；未安装到日常 IDEA。状态栏使用 IDE 自带后台任务区域，文件名/字节/速度详情可展开查看，不是新增常驻状态栏控件。

## 默认远程目录（0.1.3，2026-11-03）

- 新增应用级持久化设置：Settings → Tools → Kubernetes Files → 默认远程目录。默认留空，连接时通过非交互 `pwd -P` 读取所选容器 exec 工作目录；固定绝对路径优先，不解析 Shell 表达式。
- 自动探测或自动目录浏览失败时回退 `/` 并记录原因；固定目录不存在 / 无权限时直接报错。取消异常保持传播，不以回退掩盖取消。成功连接后把实际目录写回路径栏。
- 设置影响新面板和新的目标连接，已有 Files 页签保留当前目录。手动路径可覆盖默认值，清空路径后连接 / Enter 可重新探测。探测不是读取现有终端会话，不执行交互 Shell 初始化脚本，不保证与 Shell 初始化中 `cd` 后的路径相同。
- 新增 4 项核心测试：真实本地 shell 模拟工作目录（含空格和换行）、固定路径优先且失败不回退、自动探测失败回退与取消、自动目录不可读回退。新增 3 项设置测试：状态序列化、配置页应用 / 重置 / 拒绝相对路径、新面板采用默认值而已有面板不变。
- JDK 25 下执行 `./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure` 成功，共 42 项测试、0 失败；生成 `build/distributions/kubernetes-files-0.1.3.zip`。设置页渲染图 `build/reports/ui/browser-settings.png` 已目视检查。
- 配置检查保留原有 `262.*` 兼容提示；构建仍禁用 searchable options 生成，出现无索引提示，设置页通过 Tools 分类进入。未运行真实 Pod 工作目录探测，也未将新版安装到日常 IDEA。

## 移除独立窗口（0.1.4，2026-11-03）

- 按用户要求仅保留原生 Kubernetes Files 页签；删除独立 ToolWindow 注册、`KubernetesFiles.OpenBrowser` 动作及 BrowserService / BrowserToolWindow / OpenBrowserAction 类。缺少原生内容上下文时提示回到 Kubernetes 节点，不再回退到独立窗口。
- BrowserPanel 改为仅供原生页签使用，删除手动连接表单及发现按钮；连接目标固定由原生选择传入，保留默认目录、重新连接、传输和全局进度。
- 替换原独立窗口测试，先确认旧通用动作仍注册导致失败，修改后验证无通用动作、无 ToolWindow 描述、无手动连接按钮；原生页签生命周期和设置测试仍通过。
- JDK 25 执行 `./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure` 成功，共 42 项测试、0 失败。检查 0.1.4 安装 ZIP 内部 JAR，确认不含旧窗口注册、动作 ID 或三个旧实现类，避免增量构建残留。
- 原生布局渲染和本地测试未替代真实集群验证；未修改日常 IDEA 安装。运行旧版本的 IDE 需更新插件并重启，开发沙箱需重启 runIde 后采用新注册信息。
