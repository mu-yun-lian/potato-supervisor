package org.potato.supervisor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.potato.supervisor.rules.StudyEntry
import org.potato.supervisor.content.Dialogue
import java.time.LocalDate
import android.content.Context

@RunWith(AndroidJUnit4::class)
class V03PersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val controller get()=(context.applicationContext as SupervisorApp).controller
    @Test fun writeBeforeStop() = runBlocking {
        controller.journal.clear(); controller.journal.save(StudyEntry(LocalDate.now(),"新版重启持久化",25,"partial"))
        val d=controller.dialogue; d.clear()
        d.add("entry","roast","重启后还在的自写句子。")
        val pool=d.lines.filter { it.scene=="entry" && it.tone=="roast" }
        val first=d.select("entry","roast")
        repeat(pool.size-1) { d.select("entry","roast") }
        context.getSharedPreferences("test_expectation",Context.MODE_PRIVATE).edit().putString("first",first).commit()
        // Flush asynchronous preference writes before the explicit force-stop.
        context.getSharedPreferences("dialogue_library",Context.MODE_PRIVATE).edit().putBoolean("test_flush",true).commit()
        assertEquals("partial",controller.journal.state.first { it.ready }.entries.single().completion)
    }
    @Test fun readAfterStop() = runBlocking {
        val journal=controller.journal.state.first { it.ready }
        assertEquals("新版重启持久化",journal.entries.single().content)
        assertEquals("partial",journal.entries.single().completion)
        val d=Dialogue(context)
        assertTrue(d.lines.any { it.text=="重启后还在的自写句子。" })
        val expected=context.getSharedPreferences("test_expectation",Context.MODE_PRIVATE).getString("first",null)
        assertEquals(expected,d.select("entry","roast"))
        d.clear(); controller.journal.clear()
        context.getSharedPreferences("test_expectation",Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
}
