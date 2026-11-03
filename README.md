# Kubernetes Files

为 IntelliJ IDEA 原生 Kubernetes 插件增加容器文件浏览功能。在 Services 的 **Information / Shell** 旁打开 **Files** 页签，直接浏览和传输容器文件。

## 功能

- **原生集成**：右键 Pod 或容器打开 Files，无需重复填写连接信息；多容器 Pod 通过二级菜单选择容器。
- **目录浏览**：支持路径跳转、返回上级、刷新和双击进入目录。
- **上传下载**：支持多个普通文件上传、下载，也可从 Finder / 文件管理器拖入文件上传。
- **后台进度**：在 IDEA 底部后台任务区域查看进度和取消任务，展开可看字节数及速度；收起面板后仍继续传输。
- **传输校验**：同名文件覆盖前确认，使用临时文件和 SHA-256 校验，校验成功后提交目标文件。
- **默认目录**：自动识别容器工作目录，也可在设置中指定固定路径。

## 安装与使用

1. 在 **Settings → Plugins → ⚙ → Install Plugin from Disk…** 中选择 `build/distributions/kubernetes-files-0.1.4.zip`，按提示重启。
2. 打开 **Services → Kubernetes → Pod**，右键具体 Pod 或容器，选择 **浏览容器文件…**。
3. 在 Files 页签中浏览目录，点击 **上传文件…** 或 **下载选中文件…** 进行传输。

快捷键：macOS 为 **⌃⌥⇧K（Control + Option + Shift + K）**，Windows/Linux 为 **Ctrl + Alt + Shift + K**；使用时需选中原生 Pod / 容器节点。

本插件仅使用原生 Kubernetes 内的 Files 页签，不提供独立浏览窗口。关闭页签会取消其未完成操作，已经完成的文件不会回滚。

## 默认远程目录

在 **Settings → Tools → Kubernetes Files → 默认远程目录** 中设置：

- **留空**：自动探测容器工作目录，失败时回退到 `/` 并记录原因。
- **填写绝对路径**：例如 `/data1/weibo/bin`，新连接直接打开该目录；目录不可用时直接报错。

设置对新连接生效，已有页签保留当前目录。自动探测不跟随 Shell 内的 `cd`，也不保证与 Shell 初始化脚本切换后的目录相同。

## 使用要求

- IntelliJ IDEA **2026.2（262.*）**，已激活 Ultimate 并启用原生 Kubernetes 插件。
- 本机可执行 `kubectl`，原生连接使用本地 kubeconfig，当前身份具备目标容器的 `pods/exec` 权限。
- Linux 容器中具备 `sh`、`find`、`wc`、`cat`、`rm`、支持 `-T` 的 `mv` / `ln`，以及 `sha256sum` 或 `shasum`。
- 当前只支持普通文件传输，不支持目录、符号链接或断点续传；容器可写层文件可能随 Pod 重建丢失。

开发与验证记录见 [docs/verification.md](docs/verification.md)，实现设计见 [docs/design.md](docs/design.md)。
