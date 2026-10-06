package org.potato.supervisor

import android.app.UiAutomation
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.potato.supervisor.rules.*

@RunWith(AndroidJUnit4::class)
class LandscapeGameTest {
    companion object {
        @BeforeClass @JvmStatic fun setup() {
            Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            Configurator.getInstance().setWaitForIdleTimeout(500)
        }
    }
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(instrumentation)
    private val controller get()=(instrumentation.targetContext.applicationContext as SupervisorApp).controller
    private fun waitFor(label: String, condition: ()->Boolean) {
        val end=SystemClock.elapsedRealtime()+10_000
        while(!condition() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
        if(!condition()) {
            val metadata=runBlocking(Dispatchers.Main) {
                val service=controller.service
                val cache=service?.javaClass?.getDeclaredField("windowPackages")?.apply { isAccessible=true }?.get(service)
                val identities=if(cache is Map<*,*>) cache else cache?.javaClass?.getDeclaredField("packages")?.apply { isAccessible=true }?.get(cache)
                "windows=${service?.windows?.map { listOf(it.id,it.type,it.isFocused,it.isActive) }} identities=$identities orientation=${service?.resources?.configuration?.orientation}"
            }
            fail("$label: $metadata; ${controller.screen.value.record}")
        }
    }
    private fun start(cooldownMs: Long = 20000) {
        device.wakeUp(); device.pressHome()
        device.executeShellCommand("settings put secure enabled_accessibility_services null")
        SystemClock.sleep(300)
        device.executeShellCommand("settings put secure enabled_accessibility_services org.potato.supervisor/org.potato.supervisor.monitor.MonitorService")
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        waitFor("service") { controller.screen.value.connected }
        runBlocking {
            controller.end(); controller.agree()
            val c=Config(packages=setOf("org.potato.supervisor.test"),grantMs=4000,maxGrants=1,budgetMs=4000,cooldownMs=cooldownMs)
            controller.saveSettings(c); controller.start(c)
        }
    }
    private fun game() { device.executeShellCommand("am start -W -n org.potato.supervisor.test/org.potato.supervisor.fixture.LandscapeGameActivity") }
    @After fun end() {
        runBlocking { controller.end() }
        device.setOrientationNatural(); device.unfreezeRotation(); device.pressHome()
    }
    @Test fun immersiveSurfaceGetsEntryReminderAndAutomaticHome() {
        start(); game()
        waitFor("landscape entry reminder") { controller.screen.value.record.shouldShow() && controller.screen.value.record.reminders>0 }
        val rest=device.wait(Until.findObject(By.textContains("休息 ")),5000)
            ?: run { device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN,Until.hasObject(By.textContains("休息 "))); device.findObject(By.textContains("休息 ")) }
        assertNotNull("rest reachable in landscape",rest); rest!!.click()
        waitFor("granted") { controller.screen.value.record.grants==1 }
        waitFor("automatic HOME after landscape allowance") { controller.screen.value.record.homeSuccesses==1 && controller.screen.value.record.observation==Observation.OTHER }
    }
    @Test fun repeatedLandscapeEntriesAlwaysRemindWithoutNewAllowance() {
        start()
        repeat(8) { index ->
            game()
            waitFor("entry $index") { controller.screen.value.record.shouldShow() && controller.screen.value.record.reminders>=index+1 }
            device.pressHome(); waitFor("leave $index") { controller.screen.value.record.observation==Observation.OTHER }
        }
        assertEquals(0,controller.screen.value.record.grants)
    }
    @Test fun exhaustedLandscapeRapidReopeningStillReturnsHome() {
        start(cooldownMs=120000); game()
        waitFor("initial reminder") { controller.screen.value.record.shouldShow() && controller.screen.value.record.reminders>0 }
        val rest=device.wait(Until.findObject(By.textContains("休息 ")),5000)
            ?: run { device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN,Until.hasObject(By.textContains("休息 "))); device.findObject(By.textContains("休息 ")) }
        assertNotNull(rest); rest!!.click()
        waitFor("first HOME request") { controller.screen.value.record.homeSuccesses>=1 }
        val until=controller.screen.value.record.cooldownUntil
        repeat(8) { index ->
            val previous=controller.screen.value.record.homeSuccesses
            // Reopen immediately after HOME, including bursts before another exit is observed.
            repeat(3) {
                device.executeShellCommand("am start -n org.potato.supervisor.test/org.potato.supervisor.fixture.LandscapeGameActivity")
                SystemClock.sleep(35)
            }
            waitFor("cooldown rapid reopen $index") { controller.screen.value.record.homeSuccesses>previous && controller.screen.value.record.observation==Observation.OTHER }
            assertTrue("Launcher visible after rapid reopen $index",device.wait(Until.hasObject(By.pkg("com.google.android.apps.nexuslauncher")),5000))
            assertEquals(until,controller.screen.value.record.cooldownUntil)
            assertEquals(1,controller.screen.value.record.grants); assertEquals(0L,controller.screen.value.record.remaining)
        }
    }
}
