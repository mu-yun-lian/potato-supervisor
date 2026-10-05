# 第三方组件说明

项目自写代码与原创台词采用根目录 MIT 许可。该许可不替代依赖组件或第三方角色与游戏声音权利。

角色参考《植物大战僵尸》土豆地雷，经 AI 编辑制作表情和特效，不宣称 EA 官方素材或已获角色授权。角色图像不在代码 MIT 授权范围内。本项目与 EA 及其许可方无关联、未经其背书。来源、生成指令和分发边界见源码 ASSETS.md。

| 组件 | 使用方式 | 上游与许可 |
|---|---|---|
| Plants vs. Zombies 游戏声音 | 种植、铲除、点击、土豆地雷爆炸短音 | PopCap / EA 及相关权利人；非 MIT，来源详见 ASSETS.md 与 docs/GAME_AUDIO_SOURCES.json，未获独立授权 |
| Kotlin 标准库 2.2.21 | APK 运行依赖 | [JetBrains Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)，Apache-2.0 |
| kotlinx.coroutines 1.10.2 | APK 运行依赖 | [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)，Apache-2.0 |
| AndroidX Activity、Compose、DataStore 及其 AndroidX 传递依赖 | APK 运行依赖 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt)，Apache-2.0 |
| Gradle Wrapper 8.13 | 源码构建工具 | [Gradle](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE)，Apache-2.0 |
| Android Gradle Plugin、Compose 编译器插件 | 构建时下载 | 各组件上游许可；不随源码 ZIP 打包完整工具链 |
| AndroidX Test 与 UI Automator | 独立测试 APK 依赖 | Apache-2.0；不放入学生使用的 APK |
| JUnit 4.13.2 | 规则与设备测试依赖 | [JUnit 4](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt)，EPL-1.0；不放入学生使用的 APK |

Apache-2.0 许可正文见 `licenses/Apache-2.0.txt`。APK 构建由工具合并依赖的 META-INF 许可信息；源码交付没有把依赖缓存、SDK、JDK 或第三方应用素材作为项目自有内容。正式公开分发时应保留本文件与各上游要求的许可声明。
