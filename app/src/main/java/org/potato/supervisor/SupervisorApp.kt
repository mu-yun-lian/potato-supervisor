package org.potato.supervisor

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.potato.supervisor.content.Dialogue
import org.potato.supervisor.monitor.MonitorService
import org.potato.supervisor.rules.*
import org.potato.supervisor.storage.*

class SupervisorApp : Application() {
    lateinit var controller: SupervisorController
        private set
    override fun onCreate() { super.onCreate(); controller = SupervisorController(this) }
}

data class ScreenState(val record: Snapshot = Snapshot(), val settings: Config = Config(), val consent: Boolean = false,
    val connected: Boolean = false, val ready: Boolean = false, val error: String = "")

class SupervisorController(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sounds = org.potato.supervisor.overlay.SoundEffects(context)
    val dialogue = Dialogue(context)
    val journal = JournalRepository(context)
    private val repo = Repository(context)
    private val engine = RuleEngine()
    private val mutex = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private val mutable = MutableStateFlow(ScreenState())
    val screen: StateFlow<ScreenState> = mutable
    private var settings = Config()
    private var consent = false
    var service: MonitorService? = null
        private set
    private var error = ""
    private var savedAt = 0L
    fun now() = SystemClock.elapsedRealtime()
    fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    init {
        scope.launch {
            try {
                val stored = repo.read(); settings = stored.settings; consent = stored.consent
                var s = stored.record
                if (s.status in setOf(SessionStatus.ACTIVE, SessionStatus.INTERRUPTED)) {
                    s = if (s.bootId != boot() || boot() < 0) s.copy(status = SessionStatus.ENDED, prompt = null, endReason = "重启导致监测中断")
                    else if (now() >= s.deadline) s.copy(status = SessionStatus.ENDED, prompt = null, endReason = "学习时段结束（监测曾中断）")
                    else s.copy(status = SessionStatus.INTERRUPTED, observation = Observation.UNKNOWN, targetPackage = "", prompt = null,
                        gaps = s.gaps + 1, endReason = "进程重建，请确认恢复")
                }
                engine.replace(s, now())
                repo.write(Stored(settings, consent, s)); savedAt = now()
            } catch (_: Exception) { error = "本地记录无法读取，请清空记录并重新设置。未自动恢复监督。" }
            publish(); initialized.complete(Unit)
        }
    }
    private fun publish() { mutable.value = ScreenState(engine.state, settings, consent, service != null, true, error) }
    private suspend fun mutate(force: Boolean = true, block: () -> Unit) = withContext(Dispatchers.Main.immediate) {
        initialized.await()
        mutex.withLock {
            val old = engine.state
            val oldSettings = settings
            val oldConsent = consent
            var saving = false
            try {
                block()
                if (engine.state.prompt?.lineId == "") engine.setLine(dialogue.select(engine.state.prompt!!.scene, engine.state.config.tone))
                val s = engine.state
                val transition = old.status != s.status || old.phase != s.phase || old.grants != s.grants ||
                    old.prompt != s.prompt || old.homeHandled != s.homeHandled || old.homeSerial != s.homeSerial || old.reminders != s.reminders
                if (force || transition || now() - savedAt >= 5_000) { saving = true; repo.write(Stored(settings, consent, s)); savedAt = now() }
                error = ""
            } catch (e: Exception) {
                settings = oldSettings; consent = oldConsent
                engine.replace(old, now())
                if (saving) {
                    engine.interrupt("本地保存失败", now())
                    error = "本地保存失败，监督已中断，可立即结束。"
                } else error = e.message ?: "当前操作不可用"
            }
            publish()
        }
    }
    suspend fun saveSettings(c: Config) = mutate { require(c.error() == null) { c.error() ?: "设置无效" }; settings = c; engine.updatePresentation(c); sounds.stop() }
    suspend fun agree() = mutate { consent = true }
    suspend fun start(c: Config = settings) = mutate {
        require(consent) { "请先阅读并同意无障碍用途说明" }
        require(service != null) { "请先启用无障碍服务并等待连接" }
        require(boot() >= 0) { "无法确认本次开机身份，暂不能开始" }
        engine.start(c, now(), boot())
    }
    suspend fun resume() = mutate { require(service != null && consent) { "请先恢复权限" }; engine.recover(now(), boot()) }
    suspend fun end(reason: String = "主动结束") = mutate { engine.advance(now()); engine.end(reason) }
    suspend fun observe(o: Observation, pkg: String = "", entry: Boolean = false) = mutate(false) { engine.observe(o, pkg, entry, now()) }
    suspend fun tick() = mutate(false) { engine.advance(now()) }
    suspend fun shown(id: String) = mutate { engine.shown(id, now()) }
    suspend fun hidden() = mutate(false) { engine.hidden(now()) }
    suspend fun choose(id: String, choice: Choice) {
        val prior = screen.value.record.totalGrants
        val priorHome = screen.value.record.homeSerial
        mutate { engine.choose(id, choice, now()) }
        if(error.isBlank() && screen.value.record.totalGrants > prior) withContext(Dispatchers.Main.immediate) {
            val s=screen.value.record
            val scene=when {
                !s.canGrant() -> "last_grant"
                s.grants>1 -> "repeat_extension"
                choice==Choice.STUDY -> "accept_study"
                else -> "accept_rest"
            }
            val line=dialogue.select(scene,s.config.tone)
            android.widget.Toast.makeText(context,dialogue.renderSelected(s,line),android.widget.Toast.LENGTH_SHORT).show()
        }
        if(error.isBlank() && choice==Choice.RETURN && screen.value.record.homeSerial>priorHome) {
            val s=screen.value.record
            android.widget.Toast.makeText(context,dialogue.renderSelected(s,dialogue.select("return_to_task",s.config.tone)),android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    suspend fun fault(reason: String) = mutate { engine.interrupt(reason, now()) }
    suspend fun connect(s: MonitorService) { initialized.await(); service = s; publish() }
    suspend fun disconnect(s: MonitorService) { if (service === s) { service = null; fault("无障碍服务断开") } }
    suspend fun executeHome(s: MonitorService) = mutate {
        if (service !== s) return@mutate
        val id = engine.takeHome(now()) ?: return@mutate
        // Commit the once-only marker before issuing a system action.
        // The following write happens through the dedicated suspend method below.
        pendingHome = id
    }.also {
        val request = pendingHome; pendingHome = null
        if (request != null && service === s && engine.state.status == SessionStatus.ACTIVE && engine.state.observation == Observation.TARGET) {
            val result = s.goHome()
            mutate { engine.homeResult(result) }
        }
    }
    private var pendingHome: Long? = null
    suspend fun reset() = withContext(Dispatchers.Main.immediate) {
        initialized.await(); mutex.withLock {
            engine.end("清空记录"); sounds.stop(); publish()
            try { journal.clear(); dialogue.clear(); repo.clear(); settings = Config(); consent = false; engine.replace(Snapshot(), now()); error = "" }
            catch (_: Exception) { error = "清空失败，监督已结束，请重试" }
            publish()
        }
    }
}
