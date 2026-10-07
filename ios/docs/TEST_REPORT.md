# 0.1.0 云端编译与测试报告

日期：2026-10-07。GitHub macOS 构建机已执行 Xcode 编译与 iPhone 模拟器 XCTest；本地 Windows 静态检查单列，尚未做真实 iPhone 验证。

## 实际结果

* 主应用及三个系统扩展编译成功：`BUILD SUCCEEDED`。
* XCTest 执行 **12 个，通过 12 个，失败 0 个**：`TEST SUCCEEDED`。
* 其中 9 个是规则测试：额度/次数、冷却、快速重开时的规则状态、失败预留、重复提交、旧段隔离、剩余预算、窗口到期、会话结束、时钟异常与持久化；使用合成时钟和状态，不是真实游戏操作。
* 另 3 个是运行时资源测试：主应用 4 个土豆 PNG 及 shield 扩展 2 个 PNG 可读取解码；4 个 WAV 可由 Apple AVAudioPlayer 初始化；主应用与 shield 扩展均可读取 257 条唯一语录。

运行环境：Xcode 26.5 / Build version 17F42 / 26.5 / ProductName:		macOS / ProductVersion:		26.6.2 / BuildVersion:		25G83。模拟器：iPhone 17 Pro，com.apple.CoreSimulator.SimRuntime.iOS-26-5。

[GitHub Actions 本次执行](https://github.com/mu-yun-lian/potato-supervisor/actions/runs/37561418277)；源码提交 `71107a93e8286a03e6f68395ffbe39d4c9600731`。机器摘要见 `CI_EVIDENCE.json`；原始环境、构建和测试日志保存在 `ci/`，可核对逐条执行结果。没有生成安装用 IPA。

## 本次发现并修复

首轮执行 9 个规则测试全部通过，但主界面日志出现 `No image named 'potato-mine' found in asset catalog`。构建日志证明 PNG 已拷入应用包，主界面把独立 PNG 当成命名图片读取失败。

修复为通过 Bundle 路径加载 UIImage，主界面与 shield 共用同一读取函数，并增加运行时资源测试。复测 12 个测试全部通过，日志不再出现上述缺图告警。图片的最终视觉大小与完整动画观感没有人工验收。

## 未验证与环境限制

无签名模拟器日志仍包含 FamilyControlsAgent 连接失败和 App Group `client is not entitled`。当前没有真实 Apple 团队签名、分发权限或设备，不能把这些服务当作已经可用；本次 XCTest 不依赖授权成功。

未验证：individual 真机授权、真正的 App Group 跨进程访问、Device Activity 后台回调与短额度误差、系统 shield 对游戏的真实拦截、横屏/快速重开、撤销/重启/后台生命周期、实际听感、学习记录交互、TestFlight 与 App Store。下一步按 `DEVICE_ACCEPTANCE.md` 在配置正确的 iPhone 执行。

日志中的 AppIntents 元数据提取跳过及 XCTest 系统库不剥离告警没有导致本次失败；未使用 AppIntents，也未改动系统库。GitHub 提示旧 action 的 Node 运行时迁移，本次执行成功；不据此宣称不存在所有运行风险。

## Windows 静态检查

14 个 Swift 文件语法解析无 ERROR/missing 节点；5 个目标、148 个工程对象的引用与资源清单核对；全部 plist/entitlements/隐私清单及共享 scheme 解析；257 条语料 ID 唯一；4 图与 Android 原文件一致；4 声音输入哈希与 PCM WAV 格式/时长核对。独立摘要见 `STATIC_CHECKS.json`，不把静态检查重复算作 XCTest。
