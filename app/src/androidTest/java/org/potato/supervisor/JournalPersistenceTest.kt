package org.potato.supervisor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.potato.supervisor.rules.StudyEntry
import java.time.LocalDate

/** Run the two methods in separate instrumentation processes, with an explicit force-stop in between. */
@RunWith(AndroidJUnit4::class)
class JournalPersistenceTest {
    private val journal get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SupervisorApp).controller.journal
    @Test fun writeBeforeProcessStop() = runBlocking {
        journal.clear()
        journal.save(StudyEntry(LocalDate.now(),"持久化验收：完成二次函数复习",25))
        val saved=journal.state.first { it.ready }
        assertEquals(1,saved.entries.size); assertEquals(25,saved.entries.single().minutes)
    }
    @Test fun readAfterProcessRestart() = runBlocking {
        val saved=journal.state.first { it.ready }
        assertTrue(saved.error.isBlank()); assertEquals(1,saved.entries.size)
        assertEquals("持久化验收：完成二次函数复习",saved.entries.single().content)
        assertEquals(25,saved.entries.single().minutes)
        journal.clear()
    }
}
