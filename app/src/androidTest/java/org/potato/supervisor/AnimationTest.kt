package org.potato.supervisor

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.potato.supervisor.ui.MainActivity
import org.potato.supervisor.overlay.PotatoView
import org.potato.supervisor.rules.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class AnimationTest {
    companion object {
        @BeforeClass @JvmStatic fun automation() {
            Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            Configurator.getInstance().setWaitForIdleTimeout(500)
        }
    }
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val controller get()=(instrumentation.targetContext.applicationContext as SupervisorApp).controller
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun character(view: View): PotatoView? {
        if(view is PotatoView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) character(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun characterMovesExplodesAndStopsWhenDisabledOrDetached() {
        device.wakeUp()
        runBlocking { controller.end(); controller.saveSettings(Config(packages=setOf("org.potato.supervisor.test"),tone="roast")) }
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        var potato: PotatoView?=null
        repeat(30) {
            instrumentation.runOnMainSync { potato=character(activity.window.decorView) }
            if(potato!=null) return@repeat
            SystemClock.sleep(100)
        }
        assertNotNull(potato)
        instrumentation.runOnMainSync { assertTrue(potato!!.isMotionRunning); potato!!.performClick() }
        SystemClock.sleep(600)
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"character-normal.png"))
        instrumentation.runOnMainSync { potato!!.expression="cooldown" }
        SystemClock.sleep(600)
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"character-angry.png"))
        instrumentation.runOnMainSync { assertEquals(900L,potato!!.explode()) }
        SystemClock.sleep(450)
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"character-explosion.png"))
        SystemClock.sleep(650)
        instrumentation.runOnMainSync {
            potato!!.motionEnabled=false
            assertFalse(potato!!.isMotionRunning); assertEquals(0L,potato!!.explode())
        }
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"character-static.png"))
        instrumentation.runOnMainSync { potato!!.motionEnabled=true; assertTrue(potato!!.isMotionRunning); activity.finish() }
        var detached=false
        val deadline=SystemClock.elapsedRealtime()+8_000
        while(!detached && SystemClock.elapsedRealtime()<deadline) {
            instrumentation.runOnMainSync { detached=!potato!!.isAttachedToWindow }
            if(!detached) SystemClock.sleep(100)
        }
        instrumentation.runOnMainSync { assertFalse(potato!!.isAttachedToWindow); assertFalse(potato!!.isMotionRunning) }
    }
    @Test fun settingsUseSecondsAndHarshToneRequiresSelection() {
        device.wakeUp()
        runBlocking { controller.end(); controller.saveSettings(Config(packages=setOf("org.potato.supervisor.test"))) }
        instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.findObject(By.text("设置")),5_000)!!.click()
        val label=By.text("单次放行 / 提醒间隔（秒）")
        device.findObject(By.scrollable(true))!!.scrollUntil(Direction.DOWN,Until.hasObject(label))
        val field=device.wait(Until.findObject(By.clazz("android.widget.EditText").text("120")),3_000)
        assertNotNull(field); field!!.text="30"
        if(device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) device.pressBack()
        device.findObject(By.scrollable(true))!!.scrollUntil(Direction.DOWN,Until.hasObject(By.text("狠话")))
        device.findObject(By.text("狠话"))!!.click()
        assertTrue(device.wait(Until.hasObject(By.text("使用狠话提醒？")),3_000))
        assertEquals("snark",controller.screen.value.settings.tone)
        device.findObject(By.text("选择狠话"))!!.click()
        device.findObject(By.scrollable(true))!!.scrollUntil(Direction.DOWN,Until.hasObject(By.text("保存设置")))
        device.findObject(By.text("保存设置"))!!.click()
        val until=SystemClock.elapsedRealtime()+5_000
        while(controller.screen.value.settings.grantMs!=30_000L && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100)
        assertEquals(30_000L,controller.screen.value.settings.grantMs)
        assertEquals("roast",controller.screen.value.settings.tone)
        device.pressHome()
    }
}
