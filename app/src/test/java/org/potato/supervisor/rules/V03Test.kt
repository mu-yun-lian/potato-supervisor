package org.potato.supervisor.rules

import org.junit.Test
import org.junit.Assert.*
import org.potato.supervisor.content.RecentLines
import java.time.LocalDate

class V03Test {
    private fun w(type: Int,pkg: String?="target",focus: Boolean=false,active: Boolean=false)=WindowMetadata(type,pkg,focus,active)
    @Test fun keyboardDoesNotHideConfirmedApp() {
        assertEquals("target",ForegroundWindows.packageName(listOf(w(1,focus=true),w(2,"keyboard",active=true))))
        assertEquals("target",ForegroundWindows.packageName(listOf(w(1),w(2,"keyboard",focus=true,active=true))))
    }
    @Test fun systemObscurationAndAmbiguousAppsRemainUnknown() {
        assertNull(ForegroundWindows.packageName(listOf(w(1,focus=true),w(3,"android",active=true))))
        assertNull(ForegroundWindows.packageName(listOf(w(1,focus=true),w(1,"another"),w(2,"keyboard"))))
        assertNull(ForegroundWindows.packageName(listOf(w(1,null),w(2,"keyboard"))))
        assertNull(ForegroundWindows.packageName(listOf(w(1))))
    }
    @Test fun livePresentationKeepsRemainingBalanceAndOverlayPause() {
        val c=Config(packages=setOf("target")); val engine=RuleEngine()
        engine.start(c,0,1); engine.observe(Observation.TARGET,"target",true,0)
        engine.shown(engine.state.prompt!!.id,0)
        engine.updatePresentation(c.copy(tone="gentle",sound=true,soundVolume=20,reducedMotion=true,grantMs=1000))
        assertEquals(120000L,engine.state.config.grantMs); assertEquals("gentle",engine.state.config.tone)
        assertTrue(engine.state.config.sound); assertTrue(engine.state.config.reducedMotion)
        engine.choose(engine.state.prompt!!.id,Choice.REST,100)
        engine.advance(1100); assertEquals(119000L,engine.state.remaining)
        engine.observe(Observation.TARGET,"target",true,1100); engine.shown(engine.state.prompt!!.id,1100)
        val balance=engine.state.remaining; engine.updatePresentation(c); engine.advance(2100)
        assertEquals(balance,engine.state.remaining)
    }
    @Test fun phrasePoolCyclesWithoutRepeatsAndSurvivesHistoryReload() {
        val ids=(1..10).map { "$it" }; val recent=RecentLines()
        val first=(1..10).map { recent.select(ids)!! }
        assertEquals(10,first.distinct().size)
        val restored=RecentLines(recent.history)
        assertEquals(first.first(),restored.select(ids))
        assertNotEquals(first.last(),restored.history.last())
    }
    @Test fun otherScenesDoNotEvictSmallPool() {
        val recent=RecentLines(); val first=recent.select(listOf("a","b"))
        repeat(20) { recent.select(listOf("other$it")) }
        assertNotEquals(first,recent.select(listOf("a","b")))
        assertNull(recent.select(emptyList())); assertEquals("only",recent.select(listOf("only")))
    }
    @Test fun backupMergesWithoutLosingUnmentionedDays() {
        val day=LocalDate.of(2026,10,6)
        val old=listOf(StudyEntry(day,"原内容",20),StudyEntry(day.minusDays(1),"昨天"))
        val incoming=listOf(StudyEntry(day,"备份",30),StudyEntry(day.minusDays(2),"前天"))
        val merged=StudyJournal.mergeBackup(old,incoming,false,day)
        assertEquals(3,merged.size); assertEquals("原内容",merged.first().content)
        val overwritten=StudyJournal.mergeBackup(old,incoming,true,day)
        assertEquals("备份",overwritten.first().content); assertEquals("昨天",overwritten[1].content)
    }
    @Test fun invalidBackupRejectedBeforeMerge() {
        val day=LocalDate.now()
        for(entries in listOf(listOf(StudyEntry(day.plusDays(1),"未来")),listOf(StudyEntry(day,"a"),StudyEntry(day,"b")),listOf(StudyEntry(day,"")))) {
            try { StudyJournal.mergeBackup(emptyList(),entries,true,day); fail("Invalid backup accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun notStartedDoesNotIncreaseStudyStreak() {
        val day=LocalDate.now()
        assertEquals(0,StudyJournal.streak(listOf(StudyEntry(day,"复盘",completion="not_started")),day))
        assertEquals(1,StudyJournal.streak(listOf(StudyEntry(day,"一题",completion="partial")),day))
    }
}
