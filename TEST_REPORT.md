# v0.3.3 额度耗尽后的快速重入修复 · 2026-10-06

## 已复现的缺陷

旧规则以真实重入或缺少提醒作为冷却 HOME 请求的触发条件。HOME 被标记为已处理之后，如果快速重开时没有观察到完整 OTHER→TARGET 事件，窗口仍判定 TARGET、旧提醒仍存在，就不再创建请求。服务以前只在 UNKNOWN 时轮询窗口，稳定 TARGET 无法靠后续轮询恢复拦截。

两项新增规则测试在修改前均失败：已成功发起 HOME、快速重入缺少离开事件后不能再退出；HOME 失败后也不能重试。原始证据为 baseline-unit.log/xml。

直接安装之前交付的 PotatoSupervisor-0.3.2-fixed.apk，用新增的横屏 SurfaceView 测试：放行 4 秒耗尽，看到第一次 HOME 成功计数后立即连续重开 3 次，旧包在第 1 组重开即失败。失败时模拟器当前游戏窗口已知且 focused/active，observation=TARGET、homeSerial=homeHandled=1、homeSuccesses=1、remaining=0、entry=1。因此不是没有识别游戏，而是漏了离开事件后没有再次请求 HOME；此模拟场景重现用户描述的操作序列。详见 baseline-device.txt（1 项失败，60.512 秒）。不把它等同于已验证用户具体手机及真实游戏。

## 修复行为

冷却期间每 500 毫秒核对当前窗口，仍只读取窗口元信息。确认目标在前台且前次请求已处理时，至少距前次尝试 1 秒再创建请求，不依赖真实重入事件。一个未处理请求不会因重复内部事件叠加。连续失败最多尝试 3 次，确认离开或真实重入后恢复尝试；锁屏、UNKNOWN、非目标、结束、撤权不执行。失败计数和上次尝试时间仅用于本进程控制，不扩充保存格式。重试不发放额度、不延长固定冷却截止。爆炸按提醒编号只播放一次，同一次提醒重试不反复播放。保留之前的窗口身份修补，不恢复首页诊断提示或预览模式。

## 本次交付验证

- 离线执行 testDebugUnitTest、assembleDebugAndroidTest、assembleRelease、lintRelease，全部成功。46 项单元测试通过；lint 0 错误、7 警告。新增 4 项覆盖漏掉离开事件、失败重试、未知/锁屏/离开/结束不执行、连续失败限次及重入恢复。
- 签名后的最终交付 release APK 直接安装到隔离 Android 16 / API 36 模拟器。6 项设备测试通过：耗尽后 8 组快速重开（每组连续启动 3 次，共 24 次），每组再次返回桌面且桌面界面可见，冷却截止与额度不变；普通全屏横屏提醒和到点 HOME；8 次常规重入不加额度；通知/键盘；锁屏/旋转；撤销服务。
- 测试前仅在隔离模拟器确认首次全屏教学提示。未修改真实手机设置，测试场景只在测试 APK，生产 APK 不包含测试 Activity。
- APK versionCode 6 / versionName 0.3.3，minSdk 26，调试标记关闭。签名 v2/v3 有效，证书与旧包一致，可覆盖安装。APK 内四个游戏音效的 SHA-256 与固定来源清单一致。

未验证：用户具体设备、具体游戏、厂商游戏助手、其他安卓版本、长期耗电、所有手机音效播放、默认 2/5 分钟真实等待。本轮没有重跑完整首页/导入导出等无关流程，历史报告保留于 docs/TEST_REPORT-0.3.2-clean.md 及更早报告。

这次 HOME 后仍需核对前台是本项目的修复策略。平台接口的布尔结果说明见 [Android AccessibilityService.performGlobalAction](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#performGlobalAction(int))；它不能代替本次快速重开后的窗口核对。

APK SHA-256：`072e647b45936002fdde3d15613d547a571fe367cc53844eedca4cfd40d71d72`。证据见 verification/v0.3.3。此包用于 [GitHub v0.3.3 发布](https://github.com/mu-yun-lian/potato-supervisor/releases/tag/v0.3.3)，公开旧版 v0.3.1 保留。
