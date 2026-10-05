package org.potato.supervisor.rules

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*

class StudyJournalTest {
    private val today = LocalDate.of(2026,1,2)
    @Test fun savesOneEntryPerDayAndPreservesOtherDays() {
        val first = StudyJournal.upsert(emptyList(),StudyEntry(today.minusDays(1),"数学",30),today)
        val second = StudyJournal.upsert(first,StudyEntry(today,"英语"),today)
        val edited = StudyJournal.upsert(second,StudyEntry(today," 英语和数学 ",45),today)
        assertEquals(2,edited.size); assertEquals("英语和数学",edited.first().content)
        assertEquals(30,edited.last().minutes); assertEquals(45,edited.first().minutes)
    }
    @Test fun streakCrossesYearAndDoesNotRequireTodaysEntry() {
        val entries = (1L..3L).map { StudyEntry(today.minusDays(it),"学习") }
        assertEquals(3,StudyJournal.streak(entries,today))
        assertEquals(4,StudyJournal.streak(entries+StudyEntry(today,"学习"),today))
        assertEquals(0,StudyJournal.streak(entries,today.plusDays(1)))
    }
    @Test fun gapsAndDuplicateDatesDoNotInflateStreak() {
        assertEquals(1,StudyJournal.streak(listOf(StudyEntry(today,"a"),StudyEntry(today,"b"),StudyEntry(today.minusDays(2),"c")),today))
    }
    @Test fun optionalMinutesAreNotGuessedAndInvalidEntriesAreRejected() {
        assertNull(StudyEntry(today,"做了一题").minutes)
        assertNotNull(StudyEntry(today," ").error(today))
        assertNotNull(StudyEntry(today.plusDays(1),"a").error(today))
        assertNotNull(StudyEntry(today,"a",0).error(today))
        assertNotNull(StudyEntry(today,"a",1441).error(today))
        assertNull(StudyEntry(today,"a",1440).error(today))
    }
    @Test fun grantDefaultsAndMaximumMatchTheAgreement() {
        val config=Config(packages=setOf("example"))
        assertEquals(120_000L,config.grantMs); assertEquals(360_000L,config.budgetMs)
        assertNull(config.copy(grantMs=1000).error())
        assertNull(config.copy(grantMs=300_000).error())
        assertNotNull(config.copy(grantMs=300_001).error())
    }
}
