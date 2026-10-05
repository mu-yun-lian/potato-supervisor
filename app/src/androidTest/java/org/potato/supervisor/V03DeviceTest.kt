package org.potato.supervisor

import android.app.UiAutomation
import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
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
import org.potato.supervisor.storage.*
import org.potato.supervisor.content.Dialogue
import org.potato.supervisor.overlay.Cue
import org.json.JSONObject
import java.time.LocalDate
import java.io.File

@RunWith(AndroidJUnit4::class)
class V03DeviceTest {
    companion object {
        @BeforeClass @JvmStatic fun setup() {
            Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            Configurator.getInstance().setWaitForIdleTimeout(500)
        }
    }
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val controller get()=(context.applicationContext as SupervisorApp).controller
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun waitFor(message: String, condition: ()->Boolean) {
        val until=SystemClock.elapsedRealtime()+8000
        while(!condition() && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100)
        if(!condition()) {
            device.takeScreenshot(File(context.filesDir,"v03-failure.png")); device.dumpWindowHierarchy(File(context.filesDir,"v03-failure.xml"))
        }
        assertTrue(message,condition())
    }
    private fun find(text: String): UiObject2 {
        device.wait(Until.hasObject(By.text(text)),1200)
        if(!device.hasObject(By.text(text))) {
            if(text in setOf("设置","首页","学习记录","权限与说明"))
                device.findObject(By.scrollable(true))?.scrollUntil(Direction.UP,Until.hasObject(By.text(text)))
            else repeat(3) {
                if(!device.hasObject(By.text(text))) runCatching {
                    device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN,Until.hasObject(By.text(text)))
                }
            }
        }
        return device.wait(Until.findObject(By.text(text)),5000) ?: error("Missing: $text")
    }
    private fun launch() { device.executeShellCommand("am start -W -f 0x14000000 -n org.potato.supervisor/org.potato.supervisor.ui.MainActivity") }
    private fun begin() {
        device.wakeUp()
        if(!controller.screen.value.connected) {
            device.executeShellCommand("settings put secure enabled_accessibility_services null"); SystemClock.sleep(300)
            device.executeShellCommand("settings put secure enabled_accessibility_services org.potato.supervisor/org.potato.supervisor.monitor.MonitorService")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
        }
        waitFor("Service connected") { controller.screen.value.connected }
        runBlocking { controller.end(); controller.agree(); controller.saveSettings(Config(packages=setOf("org.potato.supervisor.test"))); controller.start() }
    }
    @After fun finish() { runBlocking { controller.end("新版测试结束") }; controller.sounds.stop(); device.pressHome() }
    @Test fun liveSettingsKeepContractAndChangeVisibleReminder() {
        begin(); device.executeShellCommand("am start -W -n org.potato.supervisor.test/org.potato.supervisor.fixture.TargetActivity")
        waitFor("Overlay shown") { controller.screen.value.record.reminders>0 }
        val before=controller.screen.value.record
        runBlocking { controller.saveSettings(controller.screen.value.settings.copy(tone="gentle",reducedMotion=true,sound=true,soundVolume=10,grantMs=30000)) }
        val after=controller.screen.value.record
        assertEquals(before.sessionId,after.sessionId); assertEquals(before.deadline,after.deadline)
        assertEquals(120000L,after.config.grantMs); assertEquals(30000L,controller.screen.value.settings.grantMs)
        assertEquals("gentle",after.config.tone); assertTrue(after.config.reducedMotion); assertTrue(after.config.sound)
        assertTrue(controller.dialogue.lines.first { it.id==after.prompt!!.lineId }.tone=="gentle")
        waitFor("New words visible") { device.hasObject(By.text(controller.dialogue.render(after))) }
        assertEquals(before.prompt!!.id,after.prompt!!.id); assertEquals(before.reminders,after.reminders)
    }
    @Test fun backupsAndReviewsPreserveExistingStudyAndRejectBadData() = runBlocking {
        val journal=controller.journal; journal.clear(); val today=LocalDate.now()
        journal.review("第一题","not_started","")
        val notStarted=journal.state.first { it.ready }.entries
        assertEquals(0,StudyJournal.streak(notStarted,today))
        journal.review("第一题","done","已完成并订正")
        val saved=journal.state.first { it.entries.singleOrNull()?.completion=="done" }.entries
        assertTrue(saved.single().content.contains("未开始")); assertEquals(1,StudyJournal.streak(saved,today))
        val backup=journal.export(); assertEquals(saved,JournalRepository.decode(backup))
        journal.clear(); journal.import(JournalRepository.decode(backup),false)
        val restored=journal.state.first { it.entries.isNotEmpty() }.entries; assertEquals(saved,restored)
        journal.import(listOf(StudyEntry(today,"同日期",45)),false)
        assertEquals(saved,journal.state.first { it.ready }.entries)
        journal.import(listOf(StudyEntry(today,"同日期",45)),true)
        assertEquals("同日期",journal.state.first { it.ready }.entries.single().content)
        journal.review("第二题","not_started","")
        assertEquals(1,StudyJournal.streak(journal.state.first { it.ready }.entries,today))
        val before=journal.export()
        try { journal.import(listOf(StudyEntry(today.plusDays(1),"未来")),true); fail("Future import accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(before,journal.export())
        assertTrue(runCatching { JournalRepository.decode("{\"version\":2,\"entries\":[{\"date\":\"$today\",\"content\":\"a\",\"minutes\":1.5}]}") }.isFailure)
        assertEquals(1,JournalRepository.decode("{\"version\":1,\"entries\":[{\"date\":\"$today\",\"content\":\"旧记录\"}]}").size)
        journal.clear()
    }
    @Test fun dialogueCyclesSurviveNewInstanceAndCustomLinesPersist() {
        val d=controller.dialogue; d.clear()
        val pool=d.lines.filter { it.scene=="entry" && it.tone=="roast" }
        val ids=pool.map { d.select("entry","roast") }
        assertEquals(pool.size,ids.distinct().size)
        val second=Dialogue(context)
        assertEquals(ids.first(),second.select("entry","roast"))
        second.add("entry","roast","少废话，先完成这道题。")
        val third=Dialogue(context)
        val custom=third.lines.single { it.text=="少废话，先完成这道题。" }
        assertEquals(custom.id,third.select("entry","roast")); third.delete(custom.id)
        assertFalse(Dialogue(context).lines.any { it.id==custom.id })
        d.clear()
    }
    @Test fun soundPoolAcceptsEachShortCueAndMuteStopsPlayback() {
        device.wakeUp(); device.pressHome(); launch(); SystemClock.sleep(900)
        val audio=context.getSystemService(AudioManager::class.java)
        val old=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        assertEquals(AudioManager.STREAM_MUSIC,(controller.sounds.javaClass.getDeclaredField("audioAttributes").apply { isAccessible=true }.get(controller.sounds) as AudioAttributes).volumeControlStream)
        assertEquals(AudioManager.STREAM_SYSTEM,AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build().volumeControlStream)
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,7,0)
            assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
            val c=Config(sound=true,soundVolume=20)
            Cue.entries.forEach { cue ->
                val stream=runBlocking(kotlinx.coroutines.Dispatchers.Main) { controller.sounds.play(cue,c) }
                println("SOUND_STREAM $cue $stream"); assertTrue("Decoded playable cue $cue",stream>0)
                SystemClock.sleep(if(cue==Cue.EXPLOSION) 2000 else 850)
            }
            device.executeShellCommand("am start -W -n org.potato.supervisor.test/org.potato.supervisor.fixture.TargetActivity")
            SystemClock.sleep(500)
            val background=runBlocking(kotlinx.coroutines.Dispatchers.Main) { controller.sounds.play(Cue.EXPLOSION,c) }
            assertTrue("Accessibility app can request short audio while target app is foreground",background>0)
            println("BACKGROUND_SOUND_STREAM $background")
            assertEquals(0,controller.sounds.play(Cue.EXPLOSION,c.copy(sound=false)))
            assertTrue(controller.sounds.status.value.contains("未开启"))
            assertEquals(0,controller.sounds.play(Cue.EXPLOSION,c.copy(soundVolume=0)))
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0)
            assertEquals(0,controller.sounds.play(Cue.APPEAR,c))
            assertTrue(controller.sounds.status.value.contains("媒体音量为 0"))
        } finally { controller.sounds.stop(); audio.setStreamVolume(AudioManager.STREAM_MUSIC,old,0) }
    }
    @Test fun endFeedbackAndCustomLibraryWorkThroughUi() {
        begin(); runBlocking { controller.journal.clear() }; launch()
        find("结束本次监督").click(); find("这次任务做到哪了？")
        assertEquals(SessionStatus.ENDED,controller.screen.value.record.status)
        find("未开始").click(); find("保存到学习记录").click()
        find("今天学了什么？")
        val saved=runBlocking { controller.journal.state.first { it.entries.isNotEmpty() } }
        assertEquals("not_started",saved.entries.single().completion)
        device.takeScreenshot(File(context.filesDir,"v03-review.png"))
        find("导出备份"); find("导入备份")
        find("设置").click(); find("查看 / 添加提醒语录").click(); find("提醒语录库")
        device.findObject(By.clazz("android.widget.EditText"))!!.text="先把这道题做完再说。"
        if(device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) device.pressBack()
        find("加入语录库").click(); find("已加入当前场景")
        assertTrue(controller.dialogue.lines.any { it.text=="先把这道题做完再说。" })
        device.takeScreenshot(File(context.filesDir,"v03-library.png"))
        runBlocking { controller.journal.clear() }; controller.dialogue.clear()
    }
    @Test fun documentPickerExportsAndImportsWithConflictChoice() {
        device.wakeUp(); runBlocking { controller.end(); controller.journal.clear(); controller.journal.save(StudyEntry(LocalDate.now(),"文件备份原内容",25)) }
        launch(); find("学习记录").click(); find("导出备份").click()
        val filename="potato-test-${SystemClock.elapsedRealtime()}.json"
        val field=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)
        assertNotNull("Document filename",field); field!!.text=filename
        if(device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) device.pressBack()
        val save=device.wait(Until.findObject(By.res("android","button1")),5000)
            ?: device.findObject(By.res("com.google.android.documentsui","action_menu_save"))
            ?: device.findObject(By.text("SAVE")) ?: device.findObject(By.text("保存")) ?: error("Document save button")
        save.click(); find("备份已导出，请保留这份文件。")
        runBlocking { controller.journal.save(StudyEntry(LocalDate.now(),"本机后来内容",40)) }
        find("导入备份").click()
        val file=device.wait(Until.findObject(By.text(filename)),8000) ?: error("Exported file visible in picker")
        file.click(); find("确认导入记录"); find("确认导入").click(); find("备份已合并。")
        assertEquals("本机后来内容",runBlocking { controller.journal.state.first { it.ready } }.entries.single().content)
        find("导入备份").click(); device.wait(Until.findObject(By.text(filename)),8000)!!.click()
        find("覆盖同日期的本机记录").click(); find("确认导入").click(); find("备份已合并。")
        waitFor("Backup replaces conflict only after explicit choice") {
            runBlocking { controller.journal.state.first { it.ready } }.entries.single().content=="文件备份原内容"
        }
        device.takeScreenshot(File(context.filesDir,"v03-backup.png"))
        runBlocking { controller.journal.clear() }
        device.executeShellCommand("rm /sdcard/Download/$filename")
    }
}
