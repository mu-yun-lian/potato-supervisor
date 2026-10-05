# v0.3.1 验证记录 · 2026-10-06

本版移除不扣额度的测试模式；声音改为固定来源的 PvZ 原版素材镜像 OGG，未剪切或合成。验证针对隔离 Android 16 / API 36 模拟器，不是用户真实手机。历史范围见 docs/TEST_REPORT-0.3.0.md。

- 离线构建成功：testDebugUnitTest、assembleDebug、assembleDebugAndroidTest、assembleRelease、lintRelease，125 个任务执行。36 项单元测试全部通过。lint 0 错误、7 警告，主要是旧 API 忽略新属性及 KTX 风格建议。
- 实际安装和运行交付的 release APK（不是用 debug APK 代替），与测试 APK 同签名；4 项不同设备测试最终通过：四种音效及无声条件、配置/授权/开始/结束/许可页、三段放行及自动 HOME/固定冷却、锁屏及横屏余额/提示。
- 音效测试确认四种声音解码、SoundPool 接受播放请求；包括另一个测试应用位于前台时的播放。明确验证播放走 STREAM_MUSIC，原 USAGE_ASSISTANCE_SONIFICATION 映射 STREAM_SYSTEM；验证应用关声、音量 0、手机媒体音量 0 不播放，并显示原因。没有人工听音，播放请求成功不等同扬声器一定可听，也未声称已比较游戏听感。
- 安装包中四个 OGG 的 SHA-256 与固定上游 blob 完全一致，没有旧 WAV。镜像提取过程未独立核验，来源及权利边界见 ASSETS.md 和 docs/GAME_AUDIO_SOURCES.json。
- 源码移除测试入口、测试爆炸和服务 preview 路径，界面测试明确断言旧入口不存在；保留设置里的音效试听。
- 正常 HOME 后收起覆盖层允许爆炸音尾部继续播放；锁屏/未知/结束/撤权停止。该停止策略经代码检查；未在真实手机逐一听音验证尾部。
- release APK 未设置 application-debuggable，签名 v2/v3 有效，证书 SHA-256 为 5f7936bb3bc0ebca376b0c554423b1c3b0b7a463ca600ae0ee16b5d287f6bc2b，与先前测试包相同。沿用本机开发证书保证更新兼容，未宣称具备商店正式发布签名。私钥未打包。
- 权限仍只有 VIBRATE 和本应用签名接收器权限；无 INTERNET、录音或广泛存储权限。

首次设备批次 4 项中 2 项失败：音效测试直接引用 Kotlin internal 的 debug 构建方法名，与 release 包名称不一致（NoSuchMethodError）；改用测试侧字段检查后通过，应用没有因此修改。锁屏后旋转的提示 ID 首次变化，未改应用代码的单独复查通过；保留该瞬态失败，不将其解释为已证实的普遍稳定性。第一批界面与三段自动退出通过，复查 2 项通过。原始记录见 verification/v0.3.1。

未验证：真实手机扬声器/蓝牙、通话混音、各品牌后台、电量、真实娱乐应用、长期学习效果。2/5 分钟实际等待本轮未重跑；历史数据不能算作本轮验收。

交付 APK SHA-256：`bca72d25f9c0ca8d4e49a2258c435a1594362932200223f012eb0dfef697a834`。

GitHub 发布连接无 workflow 写入权限，自动构建配置保留为 docs/android-ci.example.yml，仓库未启用远程 CI。本报告的构建和测试均在本机工具链与隔离模拟器完成，不宣称 GitHub Actions 已通过。
