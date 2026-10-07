# 土豆监督员 iOS 适配方案

版本：0.1.0 源码原型；日期：2026-10-07。

## 一、判断与目标

应另做原生 iOS 版。现有安卓版的无障碍服务、前台应用识别和悬浮窗依赖 Android 平台，换界面或跨平台打包不能把这些权限带到 iPhone。iOS 采用 Apple 官方的 Family Controls、Managed Settings 与 Device Activity；主界面使用 SwiftUI。当前没有共享前端或多平台业务框架需求，不为此增加 Flutter / React Native 与原生桥接层。

核心目标仍是：学生事先写下目标和任务；打开选定娱乐应用先遇到学习提醒；合理申请获得有限使用额度；达到门槛后再次限制；每日记录学习内容。语气、土豆身份与离线定位保留。不能把“带羞辱感的语句更有效”当成事实，暂保留语气选择，后续需要真实用户反馈评估效果及逆反情况。

**事实边界**：Apple 提供的系统 shield 可定制静态图、标题、说明和按钮，不能因此推导出可以插入任意跨应用动画、播放游戏爆炸声或持续监听每次前台切换。较新系统已新增 shield 按钮打开主应用的正式能力，不能继续用旧结论“一律无法打开主应用”。

**本次交付边界**：源码已写入，工程已生成；在 Windows 只做文件、语法和工程结构检查。未调用 Apple 编译器，未执行 XCTest，未验证系统能力，没有可安装包。方案可落实程度仍以之后的 iPhone 验收为准。

## 二、功能对照

| 安卓现有体验 | iOS 首版设计 | 差异及缺口 |
|---|---|---|
| 无障碍识别娱乐应用、覆盖弹窗 | 屏幕使用时间授权后选择具体应用，开始时设置系统 shield | 不使用无障碍，不掌握完整前台应用流 |
| 土豆呼吸、眨眼、闪烁、爆炸、声音 | shield 普通/生气静态图；主应用呼吸与爆炸状态、前台音效 | 不在其他应用上播放动画或声音；眨眼素材预留 |
| 解释/申请短暂使用 | 回主应用选择“搜资料”或“休息” | 不做 AI 理解；旧系统手动返回 |
| 两分钟默认、自定义频率不超过五分钟 | 每段 1–5 整分钟，默认 2 分钟 | 首版没有秒级配置；两分钟效果待测 |
| 放行期间重入还可提醒 | 放行期间允许使用，达到系统用量门槛后恢复 shield | 无法承诺每次重入再提醒 |
| 额度共享、次数限制和冷却 | 多个选定应用共享一段，默认 3 段/6 分钟/20 分钟冷却 | 已发放额度口径，不能假装读取精确实时余额 |
| 学习打卡 | 每天内容+可选自报分钟数；更新、删除、导出 | 不验证用户真实学习，不自动同步 |
| 完整本地语料与设置 | 承接 257 条语录与三档语气 | 暂无自定义、逐句屏蔽和持久化去重 |
| 记录统计与导入等增强 | 首版不做 | 不在这次原型中声称完全移植 |

不增加账号、服务器、远程知识库或跨端同步；两个平台仍各自保存数据。

## 三、最低系统与入口

运行版本定为 iOS 17.4+。individual 自用授权从 iOS 16 起可用，但本方案依赖 iOS 17.4 的 `includesPastActivity: false`，防止新放行段把较早的活动计入。不是声称 iOS 16 没有屏幕使用时间接口。

授权后使用系统 FamilyActivityPicker，只接受具体应用 token。拒绝分类和网站选择，避免未经实现的分类/网站屏蔽语义被误当成支持；用户可以重新进入选择器修改，但学习会话进行时禁止修改。授权撤销会结束监督并清空已保存选择，重新授权后重新选择。

shield 主按钮使用系统 `.close` 响应，表示结束当前受限应用交互。不是 Android 全局 HOME，也不是任意杀进程。次按钮在 **iOS 26.5+** 使用 `.openParentalControlsApp`；旧系统以手动打开主应用为基本路径，允许通知时提供可选通知。不能用私有 URL、响应链或后台强行唤醒模拟支持。

因为源码引用新枚举值，编译需含 iOS 26.5+ SDK 的 Xcode，即使最低运行版本较低。`#available` 解决运行时分支，不会补齐旧 SDK 声明。

## 四、规则与状态

### 默认值

学习时段 60 分钟，每段最多 2 分钟选定应用用量，每轮最多 3 段且总发放最多 6 分钟，冷却 20 分钟。音效默认关闭。可设置学习 15–720 分钟、每段 1–5 分钟、每轮 1–20 段、预算 1–100 分钟、冷却 15–720 分钟。预算与次数双重生效，最后一段可以缩短，例如 5 分钟预算下第三段只有 1 分钟。

| 状态 | 是否设置本项目 shield | 行为 |
|---|---|---|
| GATE | 是 | 首次进入或一段结束后的提醒；可在主应用申请 |
| ARMING | 是 | 已持久化申请预留，尚未扣额与解除屏蔽 |
| ALLOWANCE | 否 | 已成功注册监测并提交额度；多个选定应用共享 |
| COOLDOWN | 是 | 次数或预算达到限制，直到冷却到期 |
| INTERRUPTED | 否 | 监测/时间等异常导致停止，不假装监督有效 |
| ENDED | 否 | 主动结束或总学习时段结束 |

放行顺序：锁定共享状态 → 写预留 → 注册带独立标识的监测 → 提交发放次数及额度 → 原子保存 → 清除本项目 shield。失败时保留/恢复 shield、撤销新监测，错误显示给用户；恢复遇到孤立预留不新增扣额。

达到系统用量阈值后：读取当前会话与本段标识 → 验证回调属于当前段 → 更新规则状态 → 保存 → 恢复 shield → 停止该段监测。不是先退出一次应用再等待下一次识别，避免直接照搬 Android 已修复的快速重入竞态。

冷却到期恢复 GATE，只重置本轮发放次数与预算，**仍维持 shield**，不能因为冷却结束就自动无限放行。总学习截止优先于冷却和任意放行。

### 十五分钟窗口与两分钟门槛

Apple 的 15 分钟下限针对 `DeviceActivitySchedule` 区间，不是说使用门槛必须 15 分钟。本原型每次建立 **15 分钟自然时间窗口**，内部 event 门槛按本段 1–5 分钟配置，并排除监测前的活动。

用户切走或锁屏，不获得额外段；何种活动实际计入门槛由系统统计决定，需真机测量。窗口结束仍未达到用量门槛，则收回放行，已发额度不返还。这是 iOS 首版明确的新规则，避免声称可以随时读取未用余额、准确退款或把放行一直滚动延长。

本轮显示“已发放 X 段 / Y 分钟”。它不是实际用量；只有系统确认门槛的次数另行保存。原型没有逐秒余额、切出自动精确暂停或每次重开追问。回调可能延迟，不能把配置“两分钟”宣传成严格在第 120 秒阻止。

## 五、模块与工程

| Xcode 目标 | 职责 |
|---|---|
| PotatoSupervisor | SwiftUI 学习页/设置/记录、individual 授权、应用选择、申请、主动结束、前台反馈 |
| StudyMonitorExtension | Device Activity 阈值与区间结束回调；更新规则和 shield |
| StudyShieldConfigurationExtension | 从共享数据只读生成静态角色与语录；不在渲染时修改监督 |
| StudyShieldActionExtension | 系统按钮响应；新系统打开主应用，旧系统可选通知 |
| PolicyTests | 纯规则 XCTest 源码，独立于屏幕使用时间 SDK 行为 |

四个应用/扩展目标声明同一个 App Group 和 Family Controls；测试目标没有这两项权限。共享 Swift 文件包含规则、协调、持久化和语录，不引入包依赖。Info.plist 已填对应扩展入口、模块类名及共享组占位符；工程含扩展嵌入、目标依赖与共享方案。

主应用的呼吸效果和爆炸图只在前台展示，减少动态效果设置会停用呼吸动画。音效通过 ambient 音频会话播放，不绕过静音；退出前台停止播放。进入冷却时如果主应用不在前台，不承诺立即发声或展示爆炸，回来观察状态变化才显示。

## 六、一致性、生命周期与数据

主应用和扩展是多个进程。共享 JSON 使用 POSIX 文件锁串行读写、原子替换和首次解锁后文件保护。损坏/过大的文件不静默重建活跃会话；原型采用停止本项目监督的回退，避免把用户长期锁住。它牺牲故障情况下的拦截强度，必须在实际使用中评估。

每个会话和放行段有独立标识，冷却绑定轮次；旧会话/旧段回调不计入当前段。主动结束先持久化会话终止标记，再更新状态；不管保存成功与否，都尝试停止本项目监测并清除本项目命名 store。不会调用其他应用的限制 store。

主应用重新进入时核对系统监测是否还存在，恢复孤立预留、到期和授权撤销状态。前台刷新用于核对展示，不以后台定时器、静默音频或假后台任务替代系统监测。没有扩展回调就不能声称后台拦截已经执行。

计时使用包含睡眠的单调时钟与 Date 比较；正常锁屏不会因 awake-only 计时少算而误报。检测到两者偏差大于 90 秒或单调时钟倒退，会中断时段。该检查不是绝对防调时间或防重启方案，系统监测在重启、时区与日期修改后的行为仍需实测。时间信号仅用于本地时段，不用于设备指纹。

学习记录存主应用私有文件，每天一条，可替换当天内容并分享 JSON。记录是自报，不冒充实际学习成果。没有独立网络能力、远程日志或同步；系统备份由设备设置决定。详细数据边界见 `PRIVACY.md`。

individual 是自用授权，用户可以撤销授权、结束会话或删除应用。不能把 App Group、shield 或开源源码包装成不可绕过的管控工具。

## 七、已检查与待验证

当前完成：12 个 Swift 文件语法树解析；5 目标工程 OpenStep 解析及文件/依赖引用核对；plist、entitlements、隐私清单和共享方案解析；257 条语料 ID 唯一性；4 图与安卓资源字节一致；4 音效原始哈希和转码 WAV 结构/时长核对。

当前未完成：Apple SDK 类型检查、Xcode 构建、9 个 XCTest 执行、模拟器运行、iPhone 屏幕使用时间授权、扩展回调、横屏行为、真机音效和 TestFlight。静态 Swift 解析通过只说明语法结构可解析，不能证明 API 调用或签名正确。

最先执行 `DEVICE_ACCEPTANCE.md` 中的核心能力先验；重点验证短额度延迟、多个应用用量共享、窗口到期和额度耗尽后快速重开。核心先验失败时先修复平台流程，追加统计图或美术不能解决后台监督失效。

## 八、发布与成本

当前交付完整可审阅源码，不提供安装包。拿到 Mac 或 macOS 构建服务后才能进行 Apple 工具链编译，真机验证还需要 iPhone。TestFlight / App Store 需要 Apple Developer Program（官方页面当前列明 99 美元/年或当地货币），家庭控制分发权限需要账号持有人申请，主应用与三个扩展分别申请。

会员加入不等于 Family Controls 权限通过，GitHub 公开也不等于 App Store 审核通过。还需补商店图标、截图、隐私资料、分级、审核说明、素材分发条件。代码 MIT 不涵盖现有第三方游戏角色与声音；这项分发问题需要在正式上架前落实，不影响此次准备源码。

建议顺序：源码与设计交付 → macOS 首次编译修正 → iPhone 核心能力验收 → TestFlight → 再评估正式上架与非核心功能。当前不因没有开发条件而要求先购买全套硬件或承诺上架时间。

## 九、Apple 官方依据

以下来源用于核对平台约束，查询日期 2026-10-07；SDK、会员与审核条件后续分发时需复核。

* [Individual 授权与 Screen Time API（WWDC22）](https://developer.apple.com/videos/play/wwdc2022/110336/)：自用授权与可撤销/可删除边界。
* [ShieldConfiguration](https://developer.apple.com/documentation/managedsettingsui/shieldconfiguration)：系统页面可配置内容。
* [openParentalControlsApp](https://developer.apple.com/documentation/managedsettings/shieldactionresponse/openparentalcontrolsapp)：打开主应用的系统响应；官方文档结构化可用性为 iOS 26.5+。
* [intervalTooShort](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/monitoringerror/intervaltooshort)：监测区间最短 15 分钟。
* [includesPastActivity](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent/includespastactivity)：排除监测前活动，iOS 17.4+。
* [FamilyActivitySelection](https://developer.apple.com/documentation/familycontrols/familyactivityselection)：不透明 token 与撤销授权后的失效行为。
* [配置 Family Controls](https://developer.apple.com/documentation/Xcode/configuring-family-controls)、[申请分发权限](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)：开发能力和各扩展分发要求。
* [Apple Developer Program 加入](https://developer.apple.com/programs/enroll/)：会员成本。
* [mach_continuous_time](https://developer.apple.com/documentation/kernel/1646199-mach_continuous_time)：包含系统睡眠的连续时钟与建议等价接口。
* [Required Reason API 类别](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype)：本地计时用途的隐私声明。
