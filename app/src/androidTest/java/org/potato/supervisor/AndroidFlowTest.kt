package org.potato.supervisor

import android.content.Intent
import android.app.UiAutomation
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.potato.supervisor.rules.*
import org.potato.supervisor.storage.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidFlowTest {
    companion object {
        @BeforeClass @JvmStatic fun keepAccessibilityEnabled() {
            Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            Configurator.getInstance().setWaitForIdleTimeout(500)
        }
    }
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val controller get()=(instrumentation.targetContext.applicationContext as SupervisorApp).controller
    private val device get()=UiDevice.getInstance(instrumentation)
    private val target="org.potato.supervisor.test"
    private val config=Config(packages=setOf(target,"com.android.chrome"),sessionMs=180_000,grantMs=8_000,budgetMs=24_000,cooldownMs=12_000)
    private fun waitFor(message: String, timeout: Long=8_000, condition: ()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(!condition() && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100)
        if(!condition()) {
            captureFailure(message)
            fail(message)
        }
    }
    private fun captureFailure(message: String) {
        val name=message.replace(Regex("[^A-Za-z0-9]+"),"-")
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"failure-$name.png"))
        device.dumpWindowHierarchy(File(instrumentation.targetContext.filesDir,"failure-$name.xml"))
        println("FAILURE_STATE $message ${controller.screen.value.record}")
    }
    private fun openTarget() {
        device.executeShellCommand("am start -W -n $target/org.potato.supervisor.fixture.TargetActivity")
    }
    private fun begin(c: Config=config) {
        device.wakeUp()
        if(!controller.screen.value.connected) {
            // Instrumentation force-stops the target process; reconnect only in the isolated test device.
            device.executeShellCommand("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(300)
            device.executeShellCommand("settings put secure enabled_accessibility_services org.potato.supervisor/org.potato.supervisor.monitor.MonitorService")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
        }
        waitFor("Service must be enabled before instrumentation") { controller.screen.value.connected }
        runBlocking { controller.end("测试准备"); controller.agree(); controller.saveSettings(c); controller.start(c) }
        assertEquals(controller.screen.value.error,SessionStatus.ACTIVE,controller.screen.value.record.status)
        device.wakeUp(); device.pressHome(); openTarget()
        waitFor("Entry overlay visible") { controller.screen.value.record.shouldShow() && controller.screen.value.record.reminders>0 }
        assertTrue("Rest button present",device.wait(Until.hasObject(By.textContains("休息 ")),5_000))
    }
    private fun rest() {
        val b=device.wait(Until.findObject(By.textContains("休息 ")),5_000)
        assertNotNull("Rest button",b); b!!.click()
    }
    @After fun finish() { runBlocking { controller.end("测试结束") }; device.setOrientationNatural(); device.unfreezeRotation(); device.pressHome() }

    @Test fun threeGrantsThenAutomaticHomeAndFixedCooldown() {
        begin(config.copy(cooldownMs=30_000))
        for(i in 1..3) {
            rest(); waitFor("Grant $i starts") { controller.screen.value.record.grants==i && controller.screen.value.record.phase==Phase.ALLOWANCE }
            assertTrue("Third grant remains usable",controller.screen.value.record.remaining>0)
            if(i<3) waitFor("Followup $i",12_000) { controller.screen.value.record.phase==Phase.GATE && controller.screen.value.record.shouldShow() }
        }
        waitFor("Third grant exhausts",12_000) { controller.screen.value.record.phase==Phase.COOLDOWN }
        waitFor("Automatic HOME succeeds") { controller.screen.value.record.homeSuccesses>=1 && controller.screen.value.record.observation==Observation.OTHER }
        val until=controller.screen.value.record.cooldownUntil
        repeat(3) { i ->
            openTarget(); waitFor("Cooldown reentry HOME $i") { controller.screen.value.record.homeSuccesses>=i+2 && controller.screen.value.record.observation==Observation.OTHER }
            assertEquals(until,controller.screen.value.record.cooldownUntil)
        }
        waitFor("Cooldown makes exactly one new round",36_000) { controller.screen.value.record.round==2 }
        assertEquals(0,controller.screen.value.record.grants); assertEquals(3,controller.screen.value.record.totalGrants)
    }

    @Test fun leavingAndReenteringDoesNotGiveNewTime() {
        begin(); rest(); waitFor("Allow") { controller.screen.value.record.phase==Phase.ALLOWANCE }
        SystemClock.sleep(1_500); device.pressHome()
        waitFor("Leave") { controller.screen.value.record.observation==Observation.OTHER }
        val left=controller.screen.value.record.remaining
        SystemClock.sleep(1_500); assertEquals(left,controller.screen.value.record.remaining)
        openTarget(); waitFor("Reentry") { controller.screen.value.record.prompt?.scene=="reentry" }
        assertEquals(1,controller.screen.value.record.grants)
        assertTrue(controller.screen.value.record.remaining<=left)
        val continueButton=device.wait(Until.findObject(By.textContains("继续上次剩余")),5_000)
        assertNotNull(continueButton); continueButton!!.click()
        waitFor("Resume") { controller.screen.value.record.prompt==null }
        assertEquals(1,controller.screen.value.record.grants)
    }

    @Test fun notificationAndKeyboardRestoreForeground() {
        begin(config.copy(grantMs=60_000,budgetMs=180_000)); rest()
        waitFor("Allow") { controller.screen.value.record.prompt==null }
        device.openNotification()
        waitFor("Notification interrupts observation") { controller.screen.value.record.observation==Observation.UNKNOWN }
        device.pressBack()
        waitFor("Target is positively recovered after notification") { controller.screen.value.record.observation==Observation.TARGET }
        device.wait(Until.findObject(By.clazz("android.widget.EditText")),5_000)!!.click()
        assertTrue("Keyboard has actually opened before Back",device.wait(Until.hasObject(By.pkg("com.google.android.inputmethod.latin")),5_000))
        waitFor("Keyboard retains positively identified target") { controller.screen.value.record.observation==Observation.TARGET }
        val balance=controller.screen.value.record.remaining
        SystemClock.sleep(1800)
        assertTrue("Target consumption continues with keyboard visible",controller.screen.value.record.remaining<balance-1000)
        device.pressBack()
        waitFor("Target is recovered after keyboard") { controller.screen.value.record.observation==Observation.TARGET }
        assertEquals(1,controller.screen.value.record.grants)
    }

    @Test fun lockAndRotationKeepBalanceAndPrompt() {
        begin(config.copy(grantMs=60_000,budgetMs=180_000)); rest(); SystemClock.sleep(800)
        device.sleep(); waitFor("Lock detected") { controller.screen.value.record.observation==Observation.LOCKED }
        val remaining=controller.screen.value.record.remaining; SystemClock.sleep(1_000)
        assertEquals(remaining,controller.screen.value.record.remaining)
        device.wakeUp(); device.pressMenu(); device.pressHome(); openTarget()
        waitFor("Unlock reentry") { controller.screen.value.record.prompt?.scene=="reentry" }
        val id=controller.screen.value.record.prompt!!.id
        device.setOrientationLeft(); SystemClock.sleep(1_000)
        assertEquals(id,controller.screen.value.record.prompt!!.id)
        device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN,Until.hasObject(By.textContains("继续上次剩余")))
        if(!device.wait(Until.hasObject(By.textContains("继续上次剩余")),5_000)) {
            captureFailure("Continue remains reachable by scrolling in landscape")
            fail("Continue remains reachable by scrolling in landscape")
        }
        device.findObject(By.textContains("继续上次剩余"))!!.click()
        waitFor("Landscape button works") { controller.screen.value.record.prompt==null }
        assertEquals(1,controller.screen.value.record.grants)
        device.setOrientationNatural(); device.unfreezeRotation()
    }

    @Test fun remindModeContinuesWithoutAutoHome() {
        begin(config.copy(mode=Mode.REMIND,maxGrants=1)); rest()
        waitFor("Over-limit prompt",12_000) { controller.screen.value.record.phase==Phase.REMINDER }
        val b=device.wait(Until.findObject(By.text("知道了，继续使用")),5_000); assertNotNull(b); b!!.click()
        waitFor("Interval begins") { controller.screen.value.record.remaining>0 && controller.screen.value.record.prompt==null }
        assertEquals(1,controller.screen.value.record.grants); assertEquals(0,controller.screen.value.record.homeSuccesses)
        waitFor("Next interval prompt",12_000) { controller.screen.value.record.prompt?.scene=="over_limit" }
    }

    @Test fun persistedRecordRoundTripsWithoutForegroundHistory() {
        val s=Snapshot(config=config,status=SessionStatus.INTERRUPTED,sessionId="test",bootId=7,startAt=100,deadline=180_100,
            granted=8_000,grants=1,totalGrants=1,remaining=3_000,segmentUsed=5_000,used=5_000,
            targetPackage=target,observation=Observation.TARGET,reason=Reason.STUDY,reasons=listOf(Reason.STUDY),gaps=1)
        val encoded=Codec.write(Stored(config,true,s))
        assertFalse(encoded.contains("targetPackage")); assertFalse(encoded.contains("observation"))
        val decoded=Codec.read(encoded)
        assertEquals(s.remaining,decoded.record.remaining); assertEquals(1,decoded.record.totalGrants)
        assertEquals(Observation.UNKNOWN,decoded.record.observation)
        assertEquals(config,decoded.settings)
    }

    @Test fun internalNavigationDoesNotCreateAnotherEntry() {
        begin(config.copy(grantMs=60_000,budgetMs=180_000)); rest()
        val entries=controller.screen.value.record.entry
        device.wait(Until.findObject(By.text("打开内部页面")),5_000)!!.click()
        waitFor("Internal page stays target") { controller.screen.value.record.observation==Observation.TARGET }
        assertEquals(entries,controller.screen.value.record.entry)
        assertNull(controller.screen.value.record.prompt)
        device.pressBack(); SystemClock.sleep(500)
        assertEquals(entries,controller.screen.value.record.entry)
        assertEquals(1,controller.screen.value.record.grants)
    }

    @Test fun emergencyExitRequiresTenSecondsAndCleansOverlay() {
        begin()
        device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN,Until.hasObject(By.text("紧急解除")))
        device.wait(Until.findObject(By.text("紧急解除")),5_000)!!.click()
        assertEquals(SessionStatus.ACTIVE,controller.screen.value.record.status)
        val early=device.wait(Until.findObject(By.textContains("解除确认")),3_000)
        assertNotNull(early); early!!.click()
        assertEquals(SessionStatus.ACTIVE,controller.screen.value.record.status)
        val confirm=device.wait(Until.findObject(By.text("确认结束本次监督")),12_000)
        assertNotNull(confirm); confirm!!.click()
        waitFor("Emergency ends immediately after confirmation") { controller.screen.value.record.status==SessionStatus.ENDED }
        assertEquals("紧急解除",controller.screen.value.record.endReason)
        assertFalse(device.hasObject(By.text("土豆监督员")))
    }

    @Test fun revokingServiceInterruptsAndCleansOverlay() {
        begin()
        device.executeShellCommand("settings put secure enabled_accessibility_services null")
        waitFor("Revocation disconnects") { !controller.screen.value.connected && controller.screen.value.record.status==SessionStatus.INTERRUPTED }
        assertFalse(device.hasObject(By.text("土豆监督员")))
        assertEquals(0,controller.screen.value.record.grants)
    }

    @Test fun fullFiveMinuteForegroundAllowance() {
        begin(Config(packages=setOf(target),sessionMs=8*60_000L,grantMs=300_000L,budgetMs=900_000L))
        val before=SystemClock.elapsedRealtime()
        rest()
        waitFor("Production five-minute grant begins") { controller.screen.value.record.phase==Phase.ALLOWANCE }
        assertEquals(300_000L,controller.screen.value.record.granted)
        waitFor("Production five-minute followup",315_000) { controller.screen.value.record.phase==Phase.GATE && controller.screen.value.record.prompt!=null }
        val elapsed=SystemClock.elapsedRealtime()-before
        val s=controller.screen.value.record
        assertTrue("Five minutes must actually elapse: $elapsed",elapsed in 299_500..315_000)
        assertTrue("Confirmed real foreground time: ${s.used}",s.used in 300_000..315_000)
        assertEquals(1,s.grants)
        assertEquals(0L,s.remaining)
        assertTrue("Followup rendered",device.wait(Until.hasObject(By.textContains("休息 ")),5_000))
        println("FULL_DURATION_EVIDENCE elapsedMs=$elapsed confirmedUsageMs=${s.used} lateMs=${s.late} grantMs=${s.granted}")
    }
    @Test fun defaultTwoMinuteForegroundAllowance() {
        begin(Config(packages=setOf(target),sessionMs=8*60_000L))
        val before=SystemClock.elapsedRealtime(); rest()
        waitFor("Default two-minute grant begins") { controller.screen.value.record.phase==Phase.ALLOWANCE }
        assertEquals(120_000L,controller.screen.value.record.granted)
        waitFor("Actual two-minute followup",135_000) { controller.screen.value.record.phase==Phase.GATE && controller.screen.value.record.prompt!=null }
        val elapsed=SystemClock.elapsedRealtime()-before
        val s=controller.screen.value.record
        assertTrue("Two minutes must actually elapse: $elapsed",elapsed in 119_500..135_000)
        assertTrue(s.used in 120_000..135_000); assertEquals(1,s.grants); assertEquals(0L,s.remaining)
        assertTrue(device.wait(Until.hasObject(By.textContains("休息 ")),5_000))
        println("TWO_MINUTE_EVIDENCE elapsedMs=$elapsed confirmedUsageMs=${s.used} lateMs=${s.late} grantMs=${s.granted}")
    }
}
