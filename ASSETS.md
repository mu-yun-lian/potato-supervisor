# v0.3.1 游戏音效

使用原版游戏素材镜像 [FregD156/PvZ_Assets](https://github.com/FregD156/PvZ_Assets)，固定提交 `8ba94e95cfe7d0de5682f8258d3b932dbdac885c`。镜像作者称素材提取自原版 macOS 游戏 main.pak；本项目没有独立核验其提取过程，也未取得 EA 的独立授权。文件只改资源名，没有合成、剪切或重编码。

| 应用动作 | 原文件 | 本地资源 |
|---|---|---|
| 土豆出现 | sounds/plant.ogg（种植） | raw/appear.ogg |
| 土豆收起 | sounds/shovel.ogg（铲除） | raw/disappear.ogg |
| 警告提示 | sounds/tap.ogg（点击提示） | raw/warning.ogg |
| 爆炸 | sounds/potato_mine.ogg（土豆地雷） | raw/explosion.ogg |

文件来源链接、上游 blob、SHA-256、长度见 [GAME_AUDIO_SOURCES.json](docs/GAME_AUDIO_SOURCES.json)。游戏声音权利归 PopCap / EA 及相关权利人，不在本项目 MIT 许可范围内；镜像公开可下载不构成授权证明。v0.3.0 的自合成声音已从当前版本移除，历史版本说明保留在历史报告。

# 角色与语料素材

代码和本项目原创台词使用 MIT；下列土豆地雷图像所体现的第三方角色权利不在 MIT 授权范围内。AI 编辑不会消除原角色权利。本项目与 EA 及其许可方无关联、未经其背书。

| 文件 | 来源与处理 | 状态 |
|---|---|---|
| character/potato-mine.png | 第三方公开参考图，经内置 imagegen 清理棋盘背景、保持土豆地雷外形 | 角色参考图的编辑结果，不宣称 EA 官方原始素材或已获独立授权 |
| character/potato-mine-blink.png | 在上述图上仅编辑闭眼状态 | 同上 |
| character/potato-mine-angry.png | 在上述图上编辑红色身体、怒眉和咬牙表情 | 同上 |
| character/potato-mine-explosion.png | 参考红色图生成同风格火焰、土块与烟尘特效 | 生成特效，不是从游戏提取的动画 |
| drawable-nodpi/potato_mine_icon.png | 普通角色图的相同副本，用作应用图标 | 与角色图相同的权利边界 |
| dialogue.json | 本项目原创 257 条中文台词 | MIT；温和、讽刺、狠话三档 |
| 字体 | 安卓系统默认字体 | 不重新分发字体文件 |

参考来源：[公开页面](https://www.pngaaa.com/detail/1398916)、[参考图片](https://image.pngaaa.com/916/1398916-middle.png)。这是第三方转载来源，未验证为 EA 官方来源；转载站可下载不等于授予角色的独立使用许可。[EA 内容政策](https://help.ea.com/en/articles/security-and-rules/ea-content-policy/)的条件需要结合具体公开分发方式判断，开源代码本身不构成角色授权。

所有图像处理使用 Codex 内置 imagegen；没有通过脚本临摹、游戏解包或截图提取动画。角色运行时使用位图缓存、安卓 ValueAnimator 与 Canvas 绘制：呼吸、闭眼、出场弹性位移、点击跳跃、低频红光、摇晃、爆炸缩放和渐隐。它不是官方骨骼动画，也不是只有两张图交替。

生成指令要点：普通状态保持参考图身份、线条、位置和配色，仅移除棋盘背景并输出透明图；闭眼状态仅替换两眼为闭合弧线。

红色状态实际指令：
> Edit this exact Plants vs Zombies Potato Mine sprite into its furious armed warning state for an Android app. Preserve same canvas, same position/scale of rocks, half-buried potato body and red round plunger, same cartoon line work and polished shading. Make entire potato body flushed angry coral RED, furrowed brow and narrowed angry black eyes, gritted small white teeth; button glows orange-red but no flashing or text in this still sprite. Do not alter silhouette or invent limbs. One aligned sprite only, fully transparent background, no checkerboard, no UI.

爆炸状态实际指令：
> Create a matching cartoon explosion effect sprite for this Plants vs Zombies Potato Mine character, to overlay during the app's final limit warning. A SINGLE vivid orange-red small fiery burst with an irregular comic star core, golden hot center, curling beige-brown dust cloud around lower edges, a few angular dirt rocks flying outward. No intact potato, no face, no text, no logo, no UI. Match original clean bold cartoon outlines and flat polished game shading. Centered at same visual baseline with padding all edges, roughly square image, transparent background, absolutely no checkerboard or rectangle. This is one effect sprite, not a sheet, no panels. App will animate its expansion and fade.

素材与监督规则独立。减少动画、系统动画关闭、省电或图像解码异常不应使用户获得额外额度；除基础角色外的表情缺失会回退。公开发布者需要自行落实图像分发条件，可将这些文件替换为有明确许可的原创角色。
