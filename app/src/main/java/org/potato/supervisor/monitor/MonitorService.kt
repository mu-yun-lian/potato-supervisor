package org.potato.supervisor.monitor

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.*
import org.potato.supervisor.SupervisorApp
import org.potato.supervisor.overlay.Overlay
import org.potato.supervisor.rules.*
import org.potato.supervisor.ui.MainActivity

class MonitorService : AccessibilityService() {
    private val controller get() = (application as SupervisorApp).controller
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var overlay: Overlay?=null
    private var homeJob: Job?=null
    private var homeKey=""
    private var lastTarget=""
    private var wasOther=true
    private var locked=false
    private var registered=false
    private val windowPackages=mutableMapOf<Int,String>()
    private val receiver=object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action==Intent.ACTION_SCREEN_OFF) {
                locked=true; wasOther=true; lastTarget=""; overlay?.remove()
                scope.launch { controller.observe(Observation.LOCKED) }
            } else {
                locked=getSystemService(KeyguardManager::class.java).isKeyguardLocked
                scope.launch {
                    if(locked) controller.observe(Observation.LOCKED)
                    else if(controller.screen.value.record.observation==Observation.LOCKED) controller.observe(Observation.UNKNOWN)
                }
            }
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay=Overlay(this,controller)
        val filter=IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter)
        registered=true
        scope.launch {
            controller.connect(this@MonitorService)
            controller.screen.collect { state ->
                overlay?.render(state.record)
                val record=state.record
                val key="${record.sessionId}:${record.homeSerial}"
                val pending=record.homeSerial!=record.homeHandled && record.observation==Observation.TARGET && record.status==SessionStatus.ACTIVE
                if(!pending) { homeJob?.cancel(); homeJob=null; homeKey="" }
                else if(key!=homeKey) {
                    homeJob?.cancel(); homeKey=key
                    homeJob=scope.launch {
                        // The full-screen overlay already blocks the target while the brief effect plays.
                        val interval=if(record.phase==Phase.COOLDOWN && record.shouldShow()) overlay?.playLimitEffect() ?: 0 else 0
                        if(interval>0) delay(interval)
                        val current=controller.screen.value.record
                        homeJob=null
                        if(current.sessionId==record.sessionId && current.homeSerial==record.homeSerial)
                            controller.executeHome(this@MonitorService)
                    }
                }
            }
        }
        scope.launch {
            while(isActive) {
                val isLocked=getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(android.os.PowerManager::class.java).isInteractive
                if(isLocked && !locked) { locked=true; wasOther=true; lastTarget=""; controller.observe(Observation.LOCKED) }
                if(!isLocked && controller.screen.value.record.status==SessionStatus.ACTIVE && controller.screen.value.record.observation==Observation.UNKNOWN) reconcileWindows()
                controller.tick(); delay(if(isLocked) 5_000 else 500)
            }
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event==null || event.eventType !in setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,AccessibilityEvent.TYPE_WINDOWS_CHANGED)) return
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.windowId>=0) {
            event.packageName?.toString()?.let { windowPackages[event.windowId]=it }
        }
        reconcileWindows()
    }
    private fun reconcileWindows() {
        val s=controller.screen.value.record
        if(s.status!=SessionStatus.ACTIVE) { lastTarget=""; wasOther=true; windowPackages.clear(); return }
        val keyguard=getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if(keyguard) { locked=true; wasOther=true; scope.launch { controller.observe(Observation.LOCKED) }; return }
        val unlocked=locked; locked=false
        // Only window metadata. Never call getRoot(), event.source, text, title or screenshot APIs.
        val visible=runCatching { windows }.getOrDefault(emptyList())
        windowPackages.keys.retainAll(visible.map { it.id }.toSet())
        val pkg = ForegroundWindows.packageName(visible.map {
            WindowMetadata(it.type, windowPackages[it.id], it.isFocused, it.isActive)
        })
        if(pkg==null) { scope.launch { controller.observe(Observation.UNKNOWN) }; return }
        if(pkg in s.config.packages) {
            val entry=wasOther || unlocked || lastTarget!=pkg
            lastTarget=pkg; wasOther=false
            scope.launch { controller.observe(Observation.TARGET,pkg,entry) }
        } else {
            lastTarget=""; wasOther=true
            scope.launch { controller.observe(Observation.OTHER) }
        }
    }
    override fun onInterrupt() { overlay?.remove(); scope.launch { controller.observe(Observation.UNKNOWN) } }
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openHome() { startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
    override fun onUnbind(intent: Intent?): Boolean {
        overlay?.close(); controller.scope.launch { controller.disconnect(this@MonitorService) }
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        if(registered) { runCatching { unregisterReceiver(receiver) }; registered=false }
        overlay?.close(); controller.scope.launch { controller.disconnect(this@MonitorService) }; scope.cancel()
        super.onDestroy()
    }
}
