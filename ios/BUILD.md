# Mac 上的首次编译与签名

本文件是本机复现与签名说明。2026-10-07 已使用 GitHub macOS 构建机、Xcode 26.5 完成模拟器编译与 12 个 XCTest，详情见 `docs/TEST_REPORT.md`；没有在本地 Windows 执行 Xcode，也没有真机验证。

## 1. 准备环境

使用自己的 Mac 或提供 macOS/Xcode 的构建环境。安装含 iOS 26.5 或更新 SDK 的 Xcode，完成 Xcode 首次启动和命令行工具配置。最低运行系统为 iOS 17.4。App Store / TestFlight 分发另需开发者会员与相应权限；不要把模拟器构建当作屏幕使用时间能力的真机证明。

## 2. 配置四个目标

编辑 `Config/Project.xcconfig`：

```text
APP_BUNDLE_IDENTIFIER = 你的反向域名标识
APP_GROUP_IDENTIFIER = group.你的共享组标识
DEVELOPMENT_TEAM = 你的 Team ID
```

在 Apple Developer 中注册主应用及其 `.Monitor`、`.ShieldConfiguration`、`.ShieldAction` 三个扩展 App ID，确保四者均具备 Family Controls 和同一个 App Group。在 Xcode 的 Signing & Capabilities 为每个目标选择同一团队，核对自动签名生成的配置文件。修改 xcconfig 的组名不等于已注册组权限。

发布前由开发者账号的 Account Holder 分别提交主应用与三个扩展的 Family Controls 分发权限申请。能力写进 entitlements 文件并不代表苹果已批准。开发、Ad Hoc、TestFlight 与正式分发采用对应签名配置；不要把证书、描述文件、私钥提交到开源仓库。

## 3. 先检查工程，再编译

在解压出的工程根目录执行：

```sh
xcodebuild -list -project PotatoSupervisor.xcodeproj
xcodebuild -project PotatoSupervisor.xcodeproj -scheme PotatoSupervisor \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

当前版本已完成 Apple SDK 模拟器编译。更换 SDK、签名或构建配置后若报错，应保留完整报错与版本，按实际证据修复。

模拟器规则测试先查看本机可用目的设备，再替换设备名称：

```sh
xcodebuild -showdestinations -project PotatoSupervisor.xcodeproj -scheme PotatoSupervisor
xcodebuild -project PotatoSupervisor.xcodeproj -scheme PotatoSupervisor \
  -destination 'platform=iOS Simulator,name=这里替换为本机可用的 iPhone 名称' \
  CODE_SIGNING_ALLOWED=NO test
```

测试目标包含 9 个本地规则测试与 3 个包内资源测试，不证明系统授权、使用统计或扩展回调在模拟器/真机可用。测试主机应用和扩展的模拟器能力仍受具体系统环境限制。

## 4. 真机最小验证

连接 iPhone，选择主方案并运行；若系统要求，按 Apple 官方流程启用开发者模式。授权屏幕使用时间，选择一个实际使用的游戏或视频应用，设置每段 2 分钟、每轮 3 段，然后按照 `docs/DEVICE_ACCEPTANCE.md` 记录结果和延迟。

首先验证：后台是否收到 2 分钟阈值回调、是否恢复屏蔽、快速重开是否保持屏蔽、主动结束是否总能解除本项目限制。如果这些核心流程失败，应修复后再做视觉打磨或招募测试。

## 5. 分发前

添加商店所需的应用图标与截图；当前没有 App Store 图标资源目录。核对角色和音效的公开分发条件，完成隐私资料、年龄分级、审核说明及支持方式。完成四目标分发权限、真机验收和 TestFlight 验证后才能安排上架。

可选再生成工程：`python3 scripts/generate_project.py`。这会重新生成项目、共享方案、各目标 plist、entitlements 和隐私清单；团队标识仍由 xcconfig 提供。已生成的工程不要求用户先运行此工具。
