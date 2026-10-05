package org.potato.supervisor.content

import android.content.Context
import org.json.JSONObject
import org.potato.supervisor.rules.*
import org.json.JSONArray
import java.util.UUID

data class Line(val id: String, val scene: String, val tone: String, val text: String)
class Dialogue(context: Context) {
    private val prefs=context.getSharedPreferences("dialogue_library",Context.MODE_PRIVATE)
    private val builtin: List<Line> = runCatching {
        val a = JSONObject(context.assets.open("dialogue.json").bufferedReader().use { it.readText() }).getJSONArray("lines")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Line(it.getString("id"), it.getString("scene"), it.getString("tone"), it.getString("text")) } }
    }.getOrDefault(emptyList())
    private val recent=RecentLines(runCatching {
        val a=JSONArray(prefs.getString("history","[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList()))
    private var custom=runCatching {
        val a=JSONArray(prefs.getString("custom","[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { Line(it.getString("id"),it.getString("scene"),it.getString("tone"),it.getString("text")) } }
    }.getOrDefault(emptyList())
    val lines: List<Line> get()=builtin+custom
    fun isCustom(id: String)=custom.any { it.id==id }
    fun add(scene: String, tone: String, text: String) {
        require(builtin.any { it.scene==scene && it.tone==tone }) { "语气或场景无效" }
        require(text.isNotBlank() && text.length<=300 && !text.contains('{') && !text.contains('}')) { "语录填写 1–300 字，不要使用花括号" }
        require(custom.size<200) { "自写语录最多 200 条" }
        require(lines.none { it.scene==scene && it.tone==tone && it.text==text.trim() }) { "同一场景已有这句话" }
        custom=custom+Line("custom_${UUID.randomUUID()}",scene,tone,text.trim()); saveCustom()
    }
    fun delete(id: String) { custom=custom.filter { it.id!=id }; saveCustom() }
    private fun saveCustom() {
        prefs.edit().putString("custom",JSONArray().apply { custom.forEach {
            put(JSONObject().put("id",it.id).put("scene",it.scene).put("tone",it.tone).put("text",it.text))
        } }.toString()).apply()
    }
    fun clear() { custom=emptyList(); recent.history.clear(); prefs.edit().clear().apply() }
    fun select(scene: String, tone: String): String {
        val candidates = lines.filter { it.scene == scene && it.tone == tone }
        recent.history.retainAll(lines.map { it.id }.toSet())
        val chosen=recent.select(candidates.map { it.id })
        prefs.edit().putString("history",JSONArray(recent.history).toString()).apply()
        return chosen ?: "fallback"
    }
    fun render(s: Snapshot): String {
        return renderSelected(s, s.prompt?.lineId)
    }
    fun renderSelected(s: Snapshot, id: String?): String {
        val text = lines.find { it.id == id }?.text ?: "先想想你写下的任务：{task}。要回去继续吗？"
        val values = mapOf("goal" to s.config.goal, "task" to s.config.task,
            "grantDuration" to duration(if (s.remaining > 0) s.remaining else s.config.grantMs),
            "remainingDuration" to duration(s.remaining), "usedMinutes" to (s.used / 60_000).toString(),
            "grantCount" to s.grants.toString(), "extensionCount" to (s.grants - 1).coerceAtLeast(0).toString(),
            "cooldownMinutes" to duration(s.config.cooldownMs))
        var missing = false
        val rendered = Regex("\\{([A-Za-z]+)\\}").replace(text) { match -> values[match.groupValues[1]] ?: "".also { missing = true } }
        return if (missing) "先回到你写下的任务，完成第一步。" else rendered
    }
}
