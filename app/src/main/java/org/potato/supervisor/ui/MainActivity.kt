package org.potato.supervisor.ui

import android.app.Activity
import android.media.AudioManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.potato.supervisor.*
import org.potato.supervisor.overlay.PotatoView
import org.potato.supervisor.rules.*

data class AppItem(val pkg: String, val name: String)
class MainActivity : ComponentActivity() {
    private val controller get()=(application as SupervisorApp).controller
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        volumeControlStream=AudioManager.STREAM_MUSIC
        setContent { SupervisorScreen(this,controller) }
    }
    override fun onResume() {
        super.onResume()
        controller.scope.launch {
            val enabled=getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(-1)
                .any { it.resolveInfo.serviceInfo.packageName==packageName }
            if(!enabled && controller.screen.value.record.status==SessionStatus.ACTIVE) controller.fault("无障碍权限已关闭")
            else controller.observe(Observation.OTHER)
        }
    }
}

@Composable
fun SupervisorScreen(activity: Activity, controller: SupervisorController) {
    val ui by controller.screen.collectAsState()
    val coroutine=rememberCoroutineScope()
    var page by remember { mutableStateOf("home") }
    var consentDialog by remember { mutableStateOf(false) }
    var startDialog by remember { mutableStateOf(false) }
    var finishTask by remember { mutableStateOf<String?>(null) }
    var resetDialog by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    val pageScroll=rememberScrollState()
    LaunchedEffect(page) { pageScroll.scrollTo(0) }
    LaunchedEffect(Unit) { apps=withContext(Dispatchers.IO) { installedApps(activity) } }
    GardenTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(pageScroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("土豆监督员",style=MaterialTheme.typography.headlineMedium)
                GardenLabel("把手机放下，把今天过好。")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                    listOf("home" to "首页","journal" to "学习记录","settings" to "设置","help" to "权限与说明").forEach { (key,label) ->
                        TextButton(onClick={page=key},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=0.dp),
                            colors=ButtonDefaults.textButtonColors(containerColor=if(page==key) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                            Text(label,fontSize=12.sp)
                        }
                    }
                }
                if(!ui.ready) Text("正在读取本地设置…")
                else when(page) {
                    "settings" -> SettingsForm(ui.settings,apps,ui.record.status in setOf(SessionStatus.ACTIVE,SessionStatus.INTERRUPTED),controller,{page="phrases"}) { config ->
                        coroutine.launch { controller.saveSettings(config); if(controller.screen.value.error.isBlank()) { page="home"; localMessage="设置已保存" } }
                    }
                    "journal" -> JournalScreen(controller.journal)
                    "phrases" -> DialogueLibrary(controller.dialogue,ui.settings.tone)
                    "help" -> {
                        GardenCard {
                            Text("离线运行，监督由你开启",style=MaterialTheme.typography.titleLarge)
                            Text("使用窗口事件的应用包名，以及窗口类型、焦点和编号，判断所选应用进入与离开。安卓会显示可读取屏幕内容的权限提示；本应用实际不读取正文、节点树、密码、键盘输入或截图。")
                            Text("默认第三次放行用完后返回桌面，冷却内再次进入会被拦截。不能杀掉其他应用进程，也不能保证停止后台音频。")
                            Text("使用时间是可确认的目标应用前台时间；锁屏、切走、读提醒时暂停。冷却与学习时段仍按现实时间经过。")
                            Text("你可以随时从首页结束、撤销无障碍权限或卸载。系统停止后台时可能漏记，复盘会标出缺口。")
                            Text("角色有呼吸、眨眼、弹跳和额度耗尽时的发红爆炸。可减少动画、选择动作音效；语气、动效、震动和声音保存后立即生效。静音、振动、勿扰、通话和锁屏停声。")
                            Text("会话保留本次／最近一次；学习记录按日期保存在本机，可手动导出/导入备份。结束监督后可自填完成情况；未开始不计学习打卡。语录库保留避重历史，也可加入自己的话。没有账号、联网统计或自动上传。")
                            Text("角色参考植物大战僵尸土豆地雷；本项目与 EA 及其许可方无关联、未经其背书。角色图像及游戏音效权利与开源代码许可分开。")
                            Button(onClick={consentDialog=true}) { Text("阅读授权说明并打开系统设置") }
                            OutlinedButton(onClick={ activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}"))) }) { Text("应用信息／受限设置入口") }
                            OutlinedButton(onClick={resetDialog=true}) { Text("清空全部本地设置与记录") }
                            TextButton(onClick={page="licenses"}) { Text("开源组件与许可") }
                        }
                    }
                    "licenses" -> {
                        Text("开源组件与许可",style=MaterialTheme.typography.titleLarge)
                        val notices=remember { activity.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() } }
                        val license=remember { activity.assets.open("Apache-2.0.txt").bufferedReader().use { it.readText() } }
                        notices.replace("\r\n","\n").split("\n\n").forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
                        license.replace("\r\n","\n").split("\n\n").forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
                    }
                    else -> {
                        GardenHero(if(ui.record.status==SessionStatus.ACTIVE && ui.record.phase==Phase.COOLDOWN) "cooldown" else "idle",ui.settings.reducedMotion) { localMessage="土豆在。先把你写下的那件事做完。" }
                        GardenCard {
                            val s=ui.record
                            val current=if(s.status in setOf(SessionStatus.ACTIVE,SessionStatus.INTERRUPTED)) s.config else ui.settings
                            val status=when {
                                s.status==SessionStatus.INTERRUPTED -> "监测中断 · 等待你确认"
                                s.status==SessionStatus.ACTIVE && s.observation==Observation.UNKNOWN -> "暂时无法确认，使用计时暂停"
                                s.status==SessionStatus.ACTIVE && s.phase==Phase.COOLDOWN -> "冷却中 · 回到学习任务"
                                s.status==SessionStatus.ACTIVE -> "监督已开启"
                                else -> "未开启监督"
                            }
                            Text(status,style=MaterialTheme.typography.titleLarge)
                            Text("目标：${current.goal}")
                            Text("本次任务：${current.task}")
                            Text("已选 ${current.packages.size} 个应用 · 无障碍服务${if(ui.connected) "已连接" else "未连接"}")
                            if(s.status==SessionStatus.ACTIVE) {
                                Text("学习时段剩余 ${duration(s.deadline-controller.now())} · 第 ${s.round} 轮")
                                if(s.phase==Phase.COOLDOWN) Text("冷却剩余 ${duration(s.cooldownUntil-controller.now())}")
                                else Text("${if(s.remaining>0) "当前段剩余 ${duration(s.remaining)} · " else ""}之后还能领取 ${(s.config.maxGrants-s.grants).coerceAtLeast(0)} 次")
                                if(s.observation==Observation.UNKNOWN) Text("请切出目标应用后重新进入；未知区间不会被猜成使用时间。")
                            }
                            if(s.status==SessionStatus.ACTIVE || s.status==SessionStatus.INTERRUPTED) {
                                Button(onClick={coroutine.launch { controller.end(); if(controller.screen.value.error.isBlank()) finishTask=s.config.task }},modifier=Modifier.fillMaxWidth()) { Text("结束本次监督") }
                                if(s.status==SessionStatus.INTERRUPTED) OutlinedButton(onClick={coroutine.launch { controller.resume() }}) { Text("确认恢复剩余会话") }
                            } else Button(onClick={
                                if(ui.settings.error()!=null) { page="settings"; localMessage=ui.settings.error().orEmpty() }
                                else if(!ui.connected || !ui.consent) consentDialog=true else startDialog=true
                            },modifier=Modifier.fillMaxWidth()) { Text("开始学习") }
                            if(!ui.connected) OutlinedButton(onClick={consentDialog=true}) { Text("启用应用提醒权限") }
                        }
                        if(ui.record.sessionId.isNotEmpty()) GardenCard {
                            val s=ui.record
                            Text("本次记录",style=MaterialTheme.typography.titleLarge)
                            Text("已确认应用使用：${duration(s.used)}")
                            Text("放行：${s.totalGrants} 次（时段） · 提醒：${s.reminders} 次（时段）")
                            Text("主动放弃：${duration(s.forfeited)} · 迟到超时：${duration(s.late)}")
                            Text("返回桌面请求成功 ${s.homeSuccesses} 次，失败 ${s.homeFailures} 次")
                            Text("监测中断／不确定转换 ${s.gaps} 次 · 已记录未知区间 ${duration(s.unknownMs)}")
                            if(s.endReason.isNotEmpty()) Text(s.endReason)
                            if(s.status==SessionStatus.ENDED) TextButton(onClick={finishTask=s.config.task}) { Text("记录任务完成情况") }
                            Text("这些记录不能代表实际学习时长或成绩提升。",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if(ui.error.isNotBlank()) Text(ui.error,color=MaterialTheme.colorScheme.error)
                if(localMessage.isNotBlank()) Text(localMessage)
                GardenLabel("本机保存 · 无需联网 · v0.3.1")
            }
            finishTask?.let { task -> FinishReview(task,controller.journal,{finishTask=null}) { finishTask=null; page="journal" } }
            if(consentDialog) AlertDialog(onDismissRequest={consentDialog=false},title={Text("无障碍权限用途")},
                text={Text("土豆监督员使用窗口事件包名、窗口类型、焦点和编号，判断所选应用进入与离开，显示提醒，并按你同意的规则返回桌面。安卓为窗口查询要求声明内容访问能力，因此会提示可读取屏幕内容；本应用实际不读取正文、节点树、输入内容或截图。数据只在本机保存，不上传。你可随时结束、关闭权限或卸载。点击同意后由你在系统设置手动启用。")},
                confirmButton={TextButton(onClick={coroutine.launch { controller.agree(); if(controller.screen.value.error.isBlank()) {
                    consentDialog=false; activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } }}) { Text("我理解并同意") }},dismissButton={TextButton(onClick={consentDialog=false}) { Text("暂不开启") }})
            if(startDialog) AlertDialog(onDismissRequest={startDialog=false},title={Text("确认这次学习约定")},
                text={val c=ui.settings; Text("任务：${c.task}\n学习时段 ${duration(c.sessionMs)}\n每次 ${duration(c.grantMs)}，每轮最多 ${c.maxGrants} 次、共 ${duration(c.budgetMs)}。\n${if(c.mode==Mode.LIMIT) "最后一段结束会返回桌面，冷却 ${duration(c.cooldownMs)}，之后可开始新一轮。" else "只提醒，不自动退出。"}\n进入选定应用就提醒；已有余额不重领。每轮额度不是整场总上限。")},
                confirmButton={TextButton(onClick={startDialog=false; coroutine.launch { controller.start() }}) { Text("同意约定，开始") }},
                dismissButton={TextButton(onClick={startDialog=false}) { Text("取消") }})
            if(resetDialog) AlertDialog(onDismissRequest={resetDialog=false},title={Text("清空本地数据？")},text={Text("会结束监督并删除目标、寄语、设置、最近会话和全部学习打卡。此操作无法撤回。")},
                confirmButton={TextButton(onClick={resetDialog=false; coroutine.launch { controller.reset() }}) { Text("清空") }},dismissButton={TextButton(onClick={resetDialog=false}) { Text("取消") }})
        }
    }
}

@Composable
private fun SettingsForm(config: Config, apps: List<AppItem>, active: Boolean, controller: SupervisorController, openLibrary: ()->Unit, save: (Config)->Unit) {
    val soundStatus by controller.sounds.status.collectAsState()
    var goal by remember(config) { mutableStateOf(config.goal) }
    var task by remember(config) { mutableStateOf(config.task) }
    var note by remember(config) { mutableStateOf(config.note) }
    var selected by remember(config) { mutableStateOf(config.packages) }
    var session by remember(config) { mutableStateOf((config.sessionMs/60_000).toString()) }
    var grant by remember(config) { mutableStateOf((config.grantMs/1000).toString()) }
    var count by remember(config) { mutableStateOf(config.maxGrants.toString()) }
    var budget by remember(config) { mutableStateOf((config.budgetMs/60_000).toString()) }
    var cooldown by remember(config) { mutableStateOf((config.cooldownMs/60_000).toString()) }
    var mode by remember(config) { mutableStateOf(config.mode) }
    var tone by remember(config) { mutableStateOf(config.tone) }
    var vibration by remember(config) { mutableStateOf(config.vibration) }
    var reducedMotion by remember(config) { mutableStateOf(config.reducedMotion) }
    var sound by remember(config) { mutableStateOf(config.sound) }
    var volume by remember(config) { mutableFloatStateOf(config.soundVolume.toFloat()) }
    var harshDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Text("约定由你来定",style=MaterialTheme.typography.headlineMedium)
    if(active) Text("语气、震动、动效与音效保存后立即生效；任务、应用、时间额度下次生效。")
    GardenCard {
        GardenLabel("01 / 学习任务")
        OutlinedTextField(goal,{goal=it},label={Text("学习目标")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(task,{task=it},label={Text("本次具体任务")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(note,{note=it},label={Text("写给自己的话（可选）")},modifier=Modifier.fillMaxWidth())
    }
    GardenCard {
        GardenLabel("02 / 容易让你分心的应用")
        if(apps.isEmpty()) Text("没有可显示的启动应用，请稍后重试。")
        apps.forEach { app -> Row(Modifier.fillMaxWidth().toggleable(value=app.pkg in selected,role=Role.Checkbox,
            onValueChange={check -> selected=if(check) selected+app.pkg else selected-app.pkg})) {
            Checkbox(app.pkg in selected,null)
            Text(app.name,modifier=Modifier.padding(top=12.dp))
        } }
    }
    GardenCard {
        GardenLabel("03 / 提醒与额度")
        listOf("学习时段（分钟）" to (session to {v:String->session=v}),"单次放行 / 提醒间隔（秒）" to (grant to {v:String->grant=v}),
            "每轮最多放行次数" to (count to {v:String->count=v}),"每轮可发放总量（分钟）" to (budget to {v:String->budget=v}),
            "冷却（分钟）" to (cooldown to {v:String->cooldown=v})).forEach { (label,field) ->
            OutlinedTextField(field.first,field.second,label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        }
        Text("单次 1–300 秒，默认 120 秒。输入 30 就是半分钟，300 是五分钟。进入应用时立即提醒；点击放行后才开始扣时间。",style=MaterialTheme.typography.bodySmall)
        GardenLabel("学习时段最多 720 分钟；每轮最多 600 分钟、20 次；冷却最多 720 分钟。")
        Row { RadioButton(mode==Mode.LIMIT,{mode=Mode.LIMIT}); Text("约定限制",Modifier.padding(top=12.dp)); RadioButton(mode==Mode.REMIND,{mode=Mode.REMIND}); Text("只提醒",Modifier.padding(top=12.dp)) }
    }
    GardenCard {
        GardenLabel("04 / 土豆的脾气")
        listOf("gentle" to "温和","snark" to "讽刺","roast" to "狠话").forEach { (value,name) ->
            Row(Modifier.fillMaxWidth().toggleable(tone==value,role=Role.RadioButton,onValueChange={ if(value=="roast" && tone!=value) harshDialog=true else tone=value })) {
                RadioButton(tone==value,null); Text(name,Modifier.padding(top=12.dp))
            }
        }
        Text(when(tone) { "roast" -> "例如：又是再玩一会儿。你对自己说的话，怎么跟放屁一样？"; "snark" -> "例如：说好到点就停。时间到了，怎么又不算数了？"; else -> "例如：休息时间到，我们先回去完成一个小步骤。" },style=MaterialTheme.typography.bodyMedium)
        GardenLabel("狠话会直接斥责食言和拖延，带有羞辱感。强度随提醒升级，可随时换回温和。")
        Row { Checkbox(vibration,{vibration=it}); Text("提醒时轻微震动",Modifier.padding(top=12.dp)) }
        Row { Checkbox(reducedMotion,{reducedMotion=it}); Text("减少动画",Modifier.padding(top=12.dp)) }
        GardenLabel("减少动画会保留静态红色警告。")
        GardenLabel("已保存的提醒音效：${if(config.sound) "已开启" else "已关闭"} · ${config.soundVolume}%")
        TextButton(onClick=openLibrary) { Text("查看 / 添加提醒语录") }
        Row(Modifier.fillMaxWidth().toggleable(sound,role=Role.Checkbox,onValueChange={sound=it})) { Checkbox(sound,null); Text("动作音效",Modifier.padding(top=12.dp)) }
        Text("音效音量：${volume.toInt()}%")
        Slider(value=volume,onValueChange={volume=it},valueRange=0f..100f)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("出现" to org.potato.supervisor.overlay.Cue.APPEAR,"收起" to org.potato.supervisor.overlay.Cue.DISAPPEAR,
                "警告" to org.potato.supervisor.overlay.Cue.WARNING,"爆炸" to org.potato.supervisor.overlay.Cue.EXPLOSION).forEach { (label,cue) ->
                TextButton(onClick={controller.sounds.play(cue,config.copy(sound=sound,soundVolume=volume.toInt()))},enabled=sound,modifier=Modifier.weight(1f)) { Text("试听$label",fontSize=12.sp) }
            }
        }
        if(soundStatus.isNotBlank()) Text(soundStatus,style=MaterialTheme.typography.bodySmall)
        GardenLabel("默认关闭；不跟随每次闪烁反复响。静音、振动、勿扰、通话和锁屏时停声；实际响度还取决于手机媒体音量。")
    }
    if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
    Button(onClick={
        val values=listOf(session,grant,count,budget,cooldown).map { it.toLongOrNull() }
        if(values.any { it==null || it<=0 || it>100_000 }) error="请填写有效的正整数"
        else {
            val c=config.copy(goal=goal.trim(),task=task.trim(),note=note,packages=selected,sessionMs=values[0]!!*60_000,
                grantMs=values[1]!!*1000,maxGrants=values[2]!!.toInt(),budgetMs=values[3]!!*60_000,cooldownMs=values[4]!!*60_000,
                mode=mode,tone=tone,vibration=vibration,reducedMotion=reducedMotion,sound=sound,soundVolume=volume.toInt())
            error=c.error().orEmpty(); if(error.isEmpty()) save(c)
        }
    },modifier=Modifier.fillMaxWidth()) { Text("保存设置") }
    if(harshDialog) AlertDialog(onDismissRequest={harshDialog=false},title={Text("使用狠话提醒？")},
        text={Text("这一档包含直白斥责、粗口和羞辱式反问，针对反复拖延与食言。你可以随时改回温和或讽刺。")},
        confirmButton={TextButton(onClick={tone="roast"; harshDialog=false}) { Text("选择狠话") }},
        dismissButton={TextButton(onClick={harshDialog=false}) { Text("取消") }})
}

private fun installedApps(context: android.content.Context): List<AppItem> {
    val pm=context.packageManager
    val home=pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val dial=pm.resolveActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:")),PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val excluded=setOf(context.packageName,"com.android.settings","com.android.systemui","android","com.android.permissioncontroller","com.google.android.permissioncontroller",home,dial)
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
        .filter { it.activityInfo.packageName !in excluded }.map { AppItem(it.activityInfo.packageName,it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }.sortedBy { it.name }
}
