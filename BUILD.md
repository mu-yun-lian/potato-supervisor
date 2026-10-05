# 构建与安装

## 固定版本

| 工具 | 版本 |
|---|---|
| JDK | 17（本次使用 Amazon Corretto 17） |
| Gradle | 8.13，Wrapper 锁定发行包 SHA-256 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin／Compose 编译器插件 | 2.2.21 |
| compileSdk／targetSdk | 36／36，minSdk 26 |
| SDK Build Tools | 36.0.0 |
| Compose BOM | 2025.10.00 |
| DataStore | 1.1.7 |

这是经构建选择的固定组合，不声称所有组件都是全球最新。AGP 官方兼容要求见 https://developer.android.com/build/releases/agp-8-13-0-release-notes 。不通过降低 targetSdk 绕过系统限制。

## Windows

1. 准备 JDK 17，以及包含 `platforms/android-36/android.jar` 和 `build-tools/36.0.0` 的安卓 SDK。首次构建需要网络下载公开依赖，应用运行不需要网络。
2. 在 PowerShell 进入源码目录，执行：

```powershell
.\Build.ps1 -JdkPath 'C:\工具\jdk-17' -SdkPath 'C:\工具\android-sdk'
```

脚本只为当前进程设置工具路径，完成后恢复；不修改系统／用户变量或代理。输出是 `app/build/outputs/apk/debug/app-debug.apk`，测试报告为 `app/build/reports/tests/testDebugUnitTest/index.html`。

可在 Android Studio 打开项目，选择同样的 JDK／SDK。也可手动设置当前终端的 JAVA_HOME、local.properties 后运行 `gradlew.bat testDebugUnitTest assembleDebug lintDebug`。

## 网络受限时的 Gradle

默认 Wrapper 使用 Gradle 官方地址，SHA-256 验证发行包。若官方下载重定向在当前网络不可达，可从官方或镜像取得 **Gradle 8.13**，用官方发布的校验值验证，再解压，用其 `bin/gradle.bat` 执行相同任务。不关闭 TLS 或校验，不修改全局代理。

使用已校验的独立 Gradle 时可执行 `.\Build.ps1 -JdkPath 'C:\工具\jdk-17' -SdkPath 'C:\工具\android-sdk' -GradlePath 'C:\工具\gradle-8.13\bin\gradle.bat'`。

本次工具下载采用 Huawei Cloud Gradle 镜像并核对官方 SHA-256，JDK 来自 Corretto 官方，SDK 压缩包来自 Google 官方并核对仓库校验值。工具和缓存未放入源码 ZIP。

## 模拟器验收

真实应用与测试 APK 分开构建：`gradlew.bat assembleDebug assembleDebugAndroidTest`。

安装两个 APK 到隔离模拟器，启用监督员服务，然后执行：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w org.potato.supervisor.test/androidx.test.runner.AndroidJUnitRunner
```

测试 APK 提供可控目标入口，只供开发验收，不应作为面向学生的应用交付。多数测试用八秒一段、十二秒或三十秒冷却等缩短参数；`fullFiveMinuteForegroundAllowance` 会实际等待五分钟前台使用。当前默认单段两分钟、三段共六分钟，冷却二十分钟。测试期间清除或覆盖模拟器内本项目设置，请使用隔离模拟器。

## 签名

GitHub 试用 APK 以 `assembleRelease` 构建（关闭调试标记），由维护者在本机签名。当前沿用既有测试证书以允许原测试用户覆盖安装；不称作应用商店正式签名，也未提供 Google Play 发布能力。私钥不在源码、ZIP 或 Git 中。公开源码可执行 `assembleRelease` 生成未签名包，再用自己的私钥经 apksigner 签名；自己的签名不能覆盖维护者发布包。卸载会删除本地数据，请先导出学习记录。

## GitHub 自动构建示例

`docs/android-ci.example.yml` 提供 GitHub Actions 配置示例。当前发布凭据没有 workflow 写入权限，所以仓库未启用远程自动构建；本版提供本地构建和设备验证记录。具备权限的维护者可将示例放入 `.github/workflows/android.yml` 启用。工作流只需仓库只读权限，不包含签名私钥。
