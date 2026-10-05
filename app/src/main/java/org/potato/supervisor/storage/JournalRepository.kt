package org.potato.supervisor.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.potato.supervisor.rules.*
import java.time.LocalDate

private val Context.journalData by preferencesDataStore(name = "study_journal")
data class JournalState(val entries: List<StudyEntry> = emptyList(), val error: String = "", val ready: Boolean = false)

class JournalRepository(private val context: Context) {
    private val key = stringPreferencesKey("journal_v1")
    val state = context.journalData.data.map { prefs ->
        try { JournalState(decode(prefs[key]), ready = true) }
        catch (_: Exception) { JournalState(error = "学习记录读取失败，原数据已保留。", ready = true) }
    }.catch { emit(JournalState(error = "学习记录暂时无法读取，请稍后重试。", ready = true)) }
    suspend fun save(entry: StudyEntry) {
        context.journalData.edit { prefs ->
            prefs[key] = encode(StudyJournal.upsert(decode(prefs[key]), entry, LocalDate.now()))
        }
    }
    suspend fun export(): String {
        val saved=state.first { it.ready }
        check(saved.error.isBlank()) { saved.error }
        return encode(saved.entries)
    }
    suspend fun import(entries: List<StudyEntry>, overwrite: Boolean) {
        context.journalData.edit { prefs ->
            prefs[key]=encode(StudyJournal.mergeBackup(decode(prefs[key]),entries,overwrite,LocalDate.now()))
        }
    }
    suspend fun review(task: String, completion: String, content: String) {
        context.journalData.edit { prefs ->
            val entries=decode(prefs[key]); val date=LocalDate.now(); val prior=entries.find { it.date==date }
            val label=when(completion) { "done" -> "已完成"; "partial" -> "完成一部分"; else -> "未开始" }
            val text="任务：$task\n完成情况（自填）：$label" + if(content.isBlank()) "" else "\n${content.trim()}"
            val combined=prior?.content?.let { "$it\n\n$text" } ?: text
            val status=if(completion=="not_started" && prior!=null && prior.completion!="not_started") prior.completion else completion
            prefs[key]=encode(StudyJournal.upsert(entries,StudyEntry(date,combined,prior?.minutes,status),date))
        }
    }
    suspend fun delete(date: LocalDate) { context.journalData.edit { prefs -> prefs[key] = encode(decode(prefs[key]).filter { it.date != date }) } }
    suspend fun clear() { context.journalData.edit { it.clear() } }
    companion object {
        fun encode(entries: List<StudyEntry>) = JSONObject().put("version", 2).put("entries", JSONArray().apply {
            entries.forEach { e -> put(JSONObject().put("date", e.date.toString()).put("content", e.content).apply { e.minutes?.let { put("minutes", it) }; e.completion?.let { put("completion",it) } }) }
        }).toString()
        fun decode(text: String?): List<StudyEntry> {
            if (text == null) return emptyList()
            val j = JSONObject(text); require(j.getInt("version") in 1..2) { "备份版本不兼容" }
            val a = j.getJSONArray("entries")
            require(a.length()<=36600) { "记录数量过多" }
            val entries = (0 until a.length()).map { i -> val e = a.getJSONObject(i)
                if(e.has("minutes")) require(e.getDouble("minutes")==e.getInt("minutes").toDouble()) { "分钟需为整数" }
                StudyEntry(LocalDate.parse(e.getString("date")), e.getString("content"), if (e.has("minutes")) e.getInt("minutes") else null, e.optString("completion").takeIf { it.isNotBlank() })
            }
            require(entries.map { it.date }.distinct().size == entries.size)
            require(entries.all { it.error(LocalDate.MAX)==null }) { "备份含有无效记录" }
            return entries.sortedByDescending { it.date }
        }
    }
}
