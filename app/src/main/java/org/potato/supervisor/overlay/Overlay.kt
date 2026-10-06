package org.potato.supervisor.overlay

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.potato.supervisor.SupervisorController
import org.potato.supervisor.monitor.MonitorService
import org.potato.supervisor.rules.*

class Overlay(private val service: MonitorService, private val controller: SupervisorController) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: ScrollView? = null
    private var promptId: String? = null
    private var clock: TextView? = null
    private var character: PotatoView? = null
    private var speech: TextView? = null
    private var lastBurst=""
    private var emergencySession = ""
    private var emergencyUntil = 0L
    private var emergencyButton: Button? = null
    private val clockTick = object : Runnable {
        override fun run() {
            if (root == null) return
            update(controller.screen.value.record)
            handler.postDelayed(this,500)
        }
    }
    private fun dp(n: Int) = (n * service.resources.displayMetrics.density).toInt()
    private fun text(body: String, size: Float = 18f) = TextView(service).apply {
        text = body; textSize = size; setTextColor(Color.rgb(46,53,44)); gravity = Gravity.CENTER
        setPadding(dp(12),dp(8),dp(12),dp(8))
    }
    private fun rounded(color: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(18).toFloat(); stroke?.let { setStroke(dp(1),it) }
    }
    private fun button(body: String, primary: Boolean = false, action: () -> Unit) = Button(service).apply {
        text = body; textSize = 17f; minHeight = dp(56); isAllCaps = false
        background=rounded(if(primary) Color.rgb(66,106,56) else Color.rgb(239,243,226))
        setTextColor(if(primary) Color.WHITE else Color.rgb(38,60,42)); setPadding(dp(12),dp(8),dp(12),dp(8))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) }
        setOnClickListener { isEnabled = false; action() }
    }
    suspend fun playLimitEffect(): Long {
        val key="${controller.screen.value.record.sessionId}:${controller.screen.value.record.prompt?.id}"
        if(key==lastBurst) return 0
        lastBurst=key
        repeat(8) {
            if(character?.isAttachedToWindow==true) {
                val interval=character?.explode() ?: 0
                val id=promptId
                if(interval>0) handler.postDelayed({ if(root!=null && promptId==id) controller.sounds.play(Cue.EXPLOSION,controller.screen.value.settings) },270)
                else controller.sounds.play(Cue.EXPLOSION,controller.screen.value.settings)
                return interval
            }
            delay(16)
        }
        return 0
    }
    fun render(s: Snapshot) {
        if (!s.shouldShow()) {
            if(s.observation in setOf(Observation.UNKNOWN,Observation.LOCKED) || s.status!=SessionStatus.ACTIVE) controller.sounds.stop()
            remove(keepExplosion=s.status==SessionStatus.ACTIVE && s.phase==Phase.COOLDOWN && s.observation==Observation.OTHER && s.homeSerial>0 && s.homeHandled==s.homeSerial); return
        }
        if (promptId != s.prompt!!.id) {
            remove()
            try {
                val column = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(20),dp(16),dp(20),dp(24)) }
                val scroll = ScrollView(service).apply { setBackgroundColor(Color.rgb(247,245,234)); isFillViewport = true; addView(column) }
                column.addView(text(if(s.phase==Phase.COOLDOWN) "额度耗尽 · 土豆生气了" else "土豆监督员 · 到你做决定了",14f))
                character=PotatoView(service).apply { expression = if(s.phase==Phase.COOLDOWN) "cooldown" else if(s.grants>0) "suspicious" else "idle"; motionEnabled=!s.config.reducedMotion }
                column.addView(character, LinearLayout.LayoutParams(-1,dp(160)))
                speech=text(controller.dialogue.render(s),22f).apply {
                    typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); setPadding(dp(18),dp(16),dp(18),dp(16))
                    background=rounded(Color.WHITE,Color.rgb(228,231,216))
                }
                column.addView(speech)
                column.addView(text("你写下的任务\n${s.config.task}",17f))
                if (s.config.note.isNotBlank()) column.addView(text("你之前写下的话：${s.config.note}",15f))
                clock = text("",16f); column.addView(clock)
                fun choose(label: String, choice: Choice) {
                    val id = s.prompt!!.id
                    column.addView(button(label,choice==Choice.RETURN) { controller.scope.launch {
                        controller.choose(id,choice)
                        if(controller.screen.value.error.isBlank() && !controller.screen.value.record.shouldShow())
                            controller.sounds.play(Cue.DISAPPEAR,controller.screen.value.settings)
                    } })
                }
                choose(if(s.remaining>0 && s.phase==Phase.ALLOWANCE) "回去学习（放弃本次剩余时间）" else "回去学习",Choice.RETURN)
                when {
                    s.phase==Phase.COOLDOWN -> column.addView(text(s.cooldownCause,15f))
                    s.remaining>0 -> choose("继续上次剩余 ${duration(s.remaining)}",Choice.CONTINUE)
                    s.phase==Phase.REMINDER -> choose("知道了，继续使用",Choice.CONTINUE)
                    s.canGrant() -> {
                        if(s.grants+1==s.config.maxGrants || s.granted+s.nextGrant()>=s.config.budgetMs)
                            column.addView(text("这是本轮最后一次，用完将${if(s.config.mode==Mode.LIMIT) "退出并冷却" else "继续提醒"}",15f))
                        choose("我来搜学习资料 · ${duration(s.nextGrant())}",Choice.STUDY)
                        choose("休息 ${duration(s.nextGrant())}",Choice.REST)
                    }
                }
                emergencyButton = button("紧急解除") {
                    if(emergencySession!=s.sessionId || emergencyUntil==0L) {
                        emergencySession=s.sessionId; emergencyUntil=controller.now()+10_000
                        emergencyButton?.isEnabled=true; update(s)
                    } else if(controller.now()>=emergencyUntil) controller.scope.launch { controller.end("紧急解除") }
                    else emergencyButton?.isEnabled=true
                }
                column.addView(emergencyButton)
                column.addView(button("打开监督员首页／正常结束") { service.openHome() })
                column.addView(text("仅监督你选定的应用 · 可随时关闭权限",12f))
                manager.addView(scroll,WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT))
                root=scroll; promptId=s.prompt.id
                handler.post(clockTick)
                if(s.shownPrompt!=s.prompt.id) {
                    controller.sounds.play(Cue.APPEAR,controller.screen.value.settings)
                    if(s.grants>0 && s.phase!=Phase.COOLDOWN) handler.postDelayed({
                        if(promptId==s.prompt.id && root!=null) controller.sounds.play(Cue.WARNING,controller.screen.value.settings)
                    },260)
                }
                controller.scope.launch { controller.shown(s.prompt.id) }
                if(s.config.vibration) service.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(70,VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
                remove(); controller.scope.launch { controller.fault("提醒窗口创建失败") }
            }
        }
        character?.motionEnabled=!s.config.reducedMotion
        speech?.text=controller.dialogue.render(s)
        update(s)
    }
    private fun update(s: Snapshot) {
        clock?.text = if(s.phase==Phase.COOLDOWN) "冷却剩余 ${duration(s.cooldownUntil-controller.now())}" else
            "本轮已放行 ${s.grants}/${s.config.maxGrants} 次 · ${if(s.remaining>0) "当前剩余 ${duration(s.remaining)}" else "每轮 ${duration(s.config.budgetMs)}"}"
        if(emergencySession==s.sessionId && emergencyUntil>0) {
            emergencyButton?.text=if(controller.now()>=emergencyUntil) "确认结束本次监督" else "解除确认 · ${duration(emergencyUntil-controller.now())}"
            emergencyButton?.isEnabled=true
        }
    }
    fun remove(keepExplosion: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        controller.sounds.stop(includeExit=false,includeExplosion=!keepExplosion)
        val existing=root
        character?.stopMotion(); character=null
        root=null; promptId=null; clock=null; speech=null; emergencyButton=null
        if(existing!=null) { runCatching { manager.removeViewImmediate(existing) }; controller.scope.launch { controller.hidden() } }
        if(controller.screen.value.record.status!=SessionStatus.ACTIVE) { emergencySession=""; emergencyUntil=0 }
    }
    fun close() { remove(); controller.sounds.stop() }
}
