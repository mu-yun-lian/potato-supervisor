package org.potato.supervisor.rules

import java.time.LocalDate

data class StudyEntry(val date: LocalDate, val content: String, val minutes: Int? = null, val completion: String? = null) {
    fun error(today: LocalDate = LocalDate.now()): String? = when {
        completion != null && completion !in setOf("done","partial","not_started") -> "完成情况无效"
        date > today -> "不能提前记录未来的学习"
        content.isBlank() -> "写下今天实际学了什么"
        content.length > 1000 -> "学习内容最多 1000 字"
        minutes != null && minutes !in 1..1440 -> "时长可留空，填写时需为 1–1440 分钟"
        else -> null
    }
}

/** A manual journal, never inferred from time away from an entertainment app. */
object StudyJournal {
    fun upsert(entries: List<StudyEntry>, entry: StudyEntry, today: LocalDate): List<StudyEntry> {
        require(entry.error(today) == null) { entry.error(today)!! }
        return (entries.filter { it.date != entry.date } + entry.copy(content = entry.content.trim())).sortedByDescending { it.date }
    }
    fun mergeBackup(current: List<StudyEntry>, incoming: List<StudyEntry>, overwrite: Boolean, today: LocalDate): List<StudyEntry> {
        require(incoming.map { it.date }.distinct().size==incoming.size) { "备份中有重复日期" }
        incoming.forEach { require(it.error(today)==null) { it.error(today)!! } }
        val dates=current.map { it.date }.toSet()
        return (if(overwrite) current.filter { old -> incoming.none { it.date==old.date } } + incoming
            else current + incoming.filter { it.date !in dates }).sortedByDescending { it.date }
    }
    fun streak(entries: List<StudyEntry>, today: LocalDate): Int {
        val dates = entries.filter { it.completion!="not_started" }.map { it.date }.toSet()
        var day = if (today in dates) today else today.minusDays(1)
        var count = 0
        while (day in dates) { count++; day = day.minusDays(1) }
        return count
    }
}
