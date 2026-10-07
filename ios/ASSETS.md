# iOS 原型素材

代码与本项目原创台词采用 MIT。第三方角色与游戏声音的权利不包含在 MIT 中；本项目与 PopCap / EA 无关联，也未获得其背书。开源源码许可不能授予第三方素材权利。

## 图像

`Resources/potato-mine.png`、`potato-mine-blink.png`、`potato-mine-angry.png`、`potato-mine-explosion.png` 与安卓版现有资源逐字节一致，没有重新画图或生成。

普通图来自第三方土豆地雷参考图的 AI 编辑，闭眼和红色怒气图是进一步编辑，爆炸图是生成的配套特效。它们不是游戏原始动画帧。[原参考页面](https://www.pngaaa.com/detail/1398916)。iOS 主应用目前展示普通、红色和爆炸状态；闭眼素材预留，未实现安卓版完整动画组合。系统 shield 使用普通/红色静态图。

## 声音

承接安卓版固定镜像 [FregD156/PvZ_Assets](https://github.com/FregD156/PvZ_Assets/tree/8ba94e95cfe7d0de5682f8258d3b932dbdac885c) 的游戏声音。镜像宣称来自游戏提取，本项目未独立核验提取过程或取得独立授权。

| iOS 文件 | 原上游文件 | 对应动作 |
|---|---|---|
| appear.wav | sounds/plant.ogg | 开始监督 |
| disappear.wav | sounds/shovel.ogg | 主动结束 |
| warning.wav | sounds/tap.ogg | 申请放行成功 |
| explosion.wav | sounds/potato_mine.ogg | 前台观察到进入冷却 |

只用 FFmpeg 转码为 PCM 16-bit WAV，保留输入采样率与声道，没有裁剪、合成或改音高。原 OGG SHA-256 与 WAV SHA-256、时长、上游链接记录于 `docs/GAME_AUDIO_SOURCES.json`。原音质有损信息不会因为转 WAV 恢复。iOS 上的实际听感尚未验证。

## 语料

`Resources/dialogue.json` 承接项目原创 257 条中文语录，含温和、讽刺和狠话。当前按会话、轮次及已发放次数稳定选句，不完整复制 Android 的持久化去重和屏蔽策略。强烈语气只是用户偏好，没有证据证明辱骂能提高学习效果。

FFmpeg 与静态检查使用的 Python 解析器仅是本地制作工具，不打包进应用或源码 ZIP；应用依赖 Apple 系统框架，没有第三方 Swift 运行库。正式公开素材或上架前，分发者需要落实相应条件，或替换为获得明确许可的资源。
