# iOS 0.1.0 未签名设备版

[下载 IPA 与源码](https://github.com/mu-yun-lian/potato-supervisor/releases/tag/ios-v0.1.0-unsigned)。

**此 IPA 未签名，普通 iPhone 不能直接安装。没有真机授权或拦截验证。**

2026-10-07 已在 GitHub macOS 环境使用 Xcode 26.5 / iPhoneOS SDK 26.5 编译 Release / arm64 设备版，包含主应用与三个屏幕使用时间扩展。最低系统版本 iOS 17.4。本包是实际设备构建，不是模拟器程序。

- [设备构建记录](https://github.com/mu-yun-lian/potato-supervisor/actions/runs/37564343755)
- 构建提交：`175b3c6a5f310a5396300da40fee1af5f41756f2`
- IPA SHA-256：`bc56d4b132ae7a8ef8d4f158412c514fb8b5bc76458e3247ee6b91395b19ed67`
- [组件与签名状态](DEVICE_PACKAGE.json)

Bundle ID 与 App Group 仍是占位符，未包含开发者证书或设备描述文件。若要安装和验证功能，应从源码配置自己的真实团队、标识、App Group 与对应权限后重新构建并签名，见 [构建说明](../BUILD.md)。分发本项目需要主应用和相关扩展的 Family Controls 分发权限。

已通过的 12 个 XCTest 来自此前的模拟器验证；这次设备编译成功不能证明 iPhone 安装、授权、后台回调或目标应用拦截已经通过。
