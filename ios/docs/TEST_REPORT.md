# 0.1.0 实际检查报告

日期：2026-10-07。执行环境：Windows，Python 3.12；没有 Swift/Xcode/xcrun 编译工具或苹果设备。

## 已执行

* Swift 语法树解析：12 个文件，0 个 ERROR / missing 节点。工具为 tree-sitter 0.26.0、tree-sitter-swift 0.7.4。
* Xcode project.pbxproj 经 openstep-parser 2.0.3 解析；141 个工程对象，5 个目标；引用对象、源文件/资源清单、3 个扩展嵌入与依赖关系均核对。
* 全部 Info.plist、4 个 entitlements、隐私清单用 Python plistlib 解析；扩展入口、Family Controls 声明和同一 App Group 占位符核对。
* 共享 scheme XML 解析；语料 257 条且 ID 唯一。
* 4 个 PNG 与 Android 现有文件逐字节一致。
* 4 个原始 OGG 与记录的 SHA-256 一致，转换后 WAV 可被标准 wave 解析，为 PCM 16-bit，时长与已记录输入差值小于 0.02 秒。没有试听或 iOS 播放验证。

机器摘要见 `STATIC_CHECKS.json`。这些检查工具仅装在工作临时目录，没有成为应用依赖，也不打包进交付源码。

## 已写入但没有执行

`Tests/PolicyTests.swift` 包含 9 个 XCTest：三段后冷却/快速重开保持限制、失败预留不扣额/重复提交拒绝、旧段回调隔离、最后短段预算、窗口到期不捏造用量、冷却只重置一轮、会话到期优先、时间异常/重启中断、持久化往返。

**执行数为 0，通过数不作统计。** 不把解析测试文件算成测试通过。

## 尚未验证

Apple SDK 类型、Swift 隔离规则、扩展受限 API 可用性、Xcode 加载与构建、签名、模拟器、individual 授权、短门槛真实计量、扩展后台生命周期、实际 shield 布局、音效、记录交互、iPhone 横屏游戏、TestFlight 和 App Store。后续 Mac 编译可能暴露静态语法工具发现不了的问题。

没有 IPA；没有做任何 iOS 发布。Android 仓库与已发布版本不属于本次改动范围。
