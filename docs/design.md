# Kubernetes Files 设计与范围

用户已批准独立插件与原生 Kubernetes 选择上下文联动，明确要求本地文件上传、远程文件下载。
目标平台：本机 IntelliJ IDEA Ultimate 2026.2.4（262），Java 25。

## 功能
仅提供原生 Kubernetes 集成：向 Services 的右键菜单和条目工具栏添加“浏览容器文件…”入口，基于选中 Pod / 容器打开。单容器 Pod 直接打开，多容器 Pod 使用二级菜单选择；保留绑定原生选择的 Tools 动作和快捷键。
在该 Pod / 容器的右侧内容区增加 `Files: 容器名` 页签。每个目标独立面板，相同目标复用页签；关闭时释放面板并取消操作。不注册独立工具窗口、通用浏览动作或手动连接表单；缺少原生内容对象时提示回到原生节点操作。
应用级设置提供默认远程目录，留空为自动模式。新连接先使用设置值或手动路径；自动模式通过非交互 `pwd -P` 探测容器 exec 的工作目录，探测 / 目录浏览失败时回退根目录并记录原因。固定目录失败直接报错；新连接成功后把真实初始路径回写到路径栏。设置变更不改变已打开面板的目录，不读取现有交互 Shell 的状态。
连接参数固定来自原生节点，本地自动查找 kubectl；重新连接不会更改页签绑定的容器。
远端路径导航、目录双击、本地普通文件多选上传/拖放、远程普通文件下载。
后台执行，字节进度、速度、取消、超时、stderr 错误。后台任务强制从后台启动；ProgressIndicator 同时接收文件名、当前文件字节比例及速度详情，面板收起不影响状态栏任务进度。取消既支持面板按钮，也支持 IDEA 后台任务的取消入口。
覆盖须确认；上传同目录随机临时文件，SHA-256 校验成功才重命名；下载写本地临时文件，校验成功才替换。
取消/失败清理临时文件；已有目标不受影响。目标目录/符号链接拒绝覆盖。
二进制 stdin/stdout 不启用 TTY；路径经 sh 参数传递，不拼接进脚本。绑定准确 context/namespace/container，不更改全局配置。

## 边界
Linux 容器要求 sh、find、wc、cat、rm、支持 -T 的 mv/ln（GNU 或兼容 BusyBox）、sha256sum 或 shasum；不安装软件。仅传输普通文件。
不接管 Shell 拖拽，不替换 JetBrains 内置动作；Files 页签仅绑定本次明确选择的容器，不在切换 Services 节点时自动连接其他容器。
本机容器动作列表硬编码动态创建，但外层 `ServiceViewItemPopup` 和 `ServiceViewItemToolbar` 是可扩展组。内容对象 `KubernetesResourceContent` 的 `addConsoleTab()` 可添加同级页签；通过其 ContentManager 复用页签，并用 Content disposer 管理面板生命周期。没有使用反射或修改 SDK 文件，但这些 Kubernetes 内容对象方法并非稳定的专用 Files 扩展点。DataKey、descriptor 和内容 API 仅声明 262 兼容，升级重新核验。
不执行应用部署、重启或修改工作负载，不自动上传业务 WAR。

## 验证
独立 core 以本地 sh 模拟 kubectl exec，验证转义、二进制、失败、覆盖、取消、校验。
编译、描述文件检查、发行 ZIP、隔离 sandbox UI 验证；真实集群不冒充已验证。
