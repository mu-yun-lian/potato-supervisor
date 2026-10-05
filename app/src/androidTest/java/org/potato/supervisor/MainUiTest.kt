package org.potato.supervisor

import android.app.UiAutomation
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.potato.supervisor.rules.*
import java.io.File
import java.time.LocalDate
import org.potato.supervisor.storage.*

@RunWith(AndroidJUnit4::class)
class MainUiTest {
    companion object {
        @BeforeClass @JvmStatic fun setupAutomation() {
            Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            Configurator.getInstance().setWaitForIdleTimeout(500)
        }
    }
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val controller get()=(instrumentation.targetContext.applicationContext as SupervisorApp).controller
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun find(text: String): UiObject2 {
        val selector=By.text(text)
        if(!device.hasObject(selector)) device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN,Until.hasObject(selector))
        return device.wait(Until.findObject(selector),5_000) ?: run {
            device.takeScreenshot(File(instrumentation.targetContext.filesDir,"ui-failure.png"))
            device.dumpWindowHierarchy(File(instrumentation.targetContext.filesDir,"ui-failure.xml"))
            error("Missing UI: $text")
        }
    }
    private fun launch() { device.executeShellCommand("am start -W -f 0x14000000 -n org.potato.supervisor/org.potato.supervisor.ui.MainActivity") }
    private fun waitFor(condition: ()->Boolean) {
        val until=SystemClock.elapsedRealtime()+8_000
        while(!condition() && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100)
        assertTrue(condition())
    }
    @After fun finish() { runBlocking { controller.end("界面测试结束") }; device.pressHome() }
    @Test fun journalCanBeSavedEditedDeletedAndSurvivesRelaunch() {
        device.wakeUp()
        runBlocking { controller.end(); controller.journal.clear() }
        launch(); find("学习记录").click()
        val fields=device.wait(Until.findObjects(By.clazz("android.widget.EditText")),5_000)!!
        assertEquals(2,fields.size)
        fields[0].text="完成数学第一题，复习了二次函数"; fields[1].text="25"
        if(device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) device.pressBack()
        find("保存学习打卡").click(); find("学习记录已保存")
        find("首页").click(); find("学习记录").click()
        assertTrue(device.wait(Until.hasObject(By.textContains("完成数学第一题")),5_000))
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"journal-test.png"))
        val saved=runBlocking { controller.journal.state.first { it.entries.isNotEmpty() } }
        assertEquals(25,saved.entries.single().minutes)
        val encoded=JournalRepository.encode(saved.entries)
        assertEquals(saved.entries,JournalRepository.decode(encoded))
        val edits=device.findObjects(By.clazz("android.widget.EditText"))
        edits[0].text="数学第一题和第二题"; edits[1].text=""
        if(device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) device.pressBack()
        find("更新学习记录").click(); find("学习记录已保存")
        assertNull(runBlocking { controller.journal.state.first { it.entries.firstOrNull()?.content=="数学第一题和第二题" } }.entries.single().minutes)
        find("删除这天的记录").click(); find("删除").click()
        runBlocking { controller.journal.state.first { it.entries.isEmpty() } }
        find("保存学习打卡")
    }
    @Test fun migrationPreservesStatisticsAndCapsLegacySettings() {
        val old=Config(packages=setOf("target"),grantMs=300_000,budgetMs=900_000)
        val source=Stored(old,true,Snapshot(config=old,status=SessionStatus.ACTIVE,sessionId="legacy",startAt=10,deadline=1_000_000,
            remaining=300_000,granted=300_000,grants=1,totalGrants=1,used=12_000))
        fun legacy(v: Stored)=Codec.write(v).replace("\"schemaVersion\":2","\"schemaVersion\":1")
        val migrated=Codec.read(legacy(source))
        assertEquals(120_000L,migrated.settings.grantMs); assertEquals(360_000L,migrated.settings.budgetMs)
        assertEquals(300_000L,migrated.record.remaining); assertEquals(12_000L,migrated.record.used)
        val oversized=Codec.read(legacy(source.copy(settings=old.copy(grantMs=600_000),record=source.record.copy(config=old.copy(grantMs=600_000)))))
        assertEquals(300_000L,oversized.settings.grantMs); assertEquals(SessionStatus.ENDED,oversized.record.status)
        assertEquals(12_000L,oversized.record.used)
        assertEquals(migrated,Codec.read(Codec.write(migrated)))
        assertTrue(runCatching { JournalRepository.decode("{\"version\":1,\"entries\":[{}]}") }.isFailure)
    }
    @Test fun configureConsentStartEndAndLicensesOffline() {
        device.wakeUp()
        if(!controller.screen.value.connected) {
            device.executeShellCommand("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(300)
            device.executeShellCommand("settings put secure enabled_accessibility_services org.potato.supervisor/org.potato.supervisor.monitor.MonitorService")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
        }
        waitFor { controller.screen.value.connected }
        runBlocking { controller.end(); controller.reset() }
        launch(); find("设置").click()
        val fields=device.wait(Until.findObjects(By.clazz("android.widget.EditText")),5_000)
        assertNotNull(fields); assertTrue(fields!!.size>=3)
        fields[0].text="完成数学练习"; fields[1].text="先完成第一道题"; fields[2].text="做完这一题再休息。"
        if(device.hasObject(By.pkg("com.google.android.inputmethod.latin")) || device.hasObject(By.pkg("com.android.inputmethod.latin"))) device.pressBack()
        find("监督测试入口").click()
        find("保存设置").click()
        waitFor { controller.screen.value.settings.packages.contains("org.potato.supervisor.test") }
        assertEquals("完成数学练习",controller.screen.value.settings.goal)
        assertFalse(device.hasObject(By.text("测试土豆提醒（不扣额度）")))
        find("开始学习").click()
        find("暂不开启").click()
        assertFalse(controller.screen.value.consent)
        find("开始学习").click(); find("我理解并同意").click()
        waitFor { controller.screen.value.consent }
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),5_000))
        device.pressBack(); find("开始学习").click(); find("同意约定，开始").click()
        waitFor { controller.screen.value.record.status==SessionStatus.ACTIVE }
        assertEquals(120_000L,controller.screen.value.record.config.grantMs)
        assertEquals(3,controller.screen.value.record.config.maxGrants)
        assertTrue(device.wait(Until.gone(By.text("确认这次学习约定")),3_000))
        SystemClock.sleep(550)
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"home-test.png"))
        device.executeShellCommand("am start -W -n org.potato.supervisor.test/org.potato.supervisor.fixture.TargetActivity")
        waitFor { controller.screen.value.record.shouldShow() && controller.screen.value.record.reminders>0 }
        SystemClock.sleep(600)
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"overlay-test.png"))
        launch(); find("结束本次监督").click()
        waitFor { controller.screen.value.record.status==SessionStatus.ENDED }
        find("暂不记录").click()
        find("权限与说明").click(); find("开源组件与许可").click()
        device.takeScreenshot(File(instrumentation.targetContext.filesDir,"licenses-test.png"))
        assertTrue(device.wait(Until.hasObject(By.textContains("第三方组件说明")),5_000))
        assertEquals(0L,controller.screen.value.record.granted)
    }
}
