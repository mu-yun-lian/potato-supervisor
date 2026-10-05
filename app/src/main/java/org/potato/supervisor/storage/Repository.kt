package org.potato.supervisor.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.potato.supervisor.rules.*

private val Context.supervisorData by preferencesDataStore(name = "supervisor")
data class Stored(val settings: Config = Config(), val consent: Boolean = false, val record: Snapshot = Snapshot())

class Repository(private val context: Context) {
    private val key = stringPreferencesKey("state_v1")
    suspend fun read(): Stored = context.supervisorData.data.first()[key]?.let { Codec.read(it) } ?: Stored()
    suspend fun write(value: Stored) { context.supervisorData.edit { it[key] = Codec.write(value) } }
    suspend fun clear() { context.supervisorData.edit { it.clear() } }
}

object Codec {
    fun config(c: Config) = JSONObject().apply {
        put("goal", c.goal); put("task", c.task); put("note", c.note); put("packages", JSONArray(c.packages.sorted()))
        put("sessionMs", c.sessionMs); put("grantMs", c.grantMs); put("maxGrants", c.maxGrants)
        put("budgetMs", c.budgetMs); put("cooldownMs", c.cooldownMs); put("mode", c.mode.name)
        put("tone", c.tone); put("vibration", c.vibration); put("reducedMotion", c.reducedMotion); put("sound", c.sound); put("soundVolume", c.soundVolume)
    }
    private fun config(j: JSONObject): Config = Config(
        goal = j.getString("goal"), task = j.getString("task"), note = j.getString("note"),
        packages = j.getJSONArray("packages").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
        sessionMs = j.getLong("sessionMs"), grantMs = j.getLong("grantMs"), maxGrants = j.getInt("maxGrants"),
        budgetMs = j.getLong("budgetMs"), cooldownMs = j.getLong("cooldownMs"), mode = Mode.valueOf(j.getString("mode")),
        tone = j.getString("tone"), vibration = j.getBoolean("vibration"), reducedMotion = j.getBoolean("reducedMotion"), sound=j.optBoolean("sound",false), soundVolume=j.optInt("soundVolume",35)
    )
    fun write(v: Stored): String = JSONObject().apply {
        put("schemaVersion", 2); put("settings", config(v.settings)); put("consent", v.consent)
        put("record", JSONObject().apply {
            val s = v.record
            put("config", config(s.config)); put("status", s.status.name); put("phase", s.phase.name)
            put("sessionId", s.sessionId); put("bootId", s.bootId); put("startAt", s.startAt); put("deadline", s.deadline)
            put("round", s.round); put("granted", s.granted); put("grants", s.grants); put("totalGrants", s.totalGrants); put("remaining", s.remaining)
            put("segmentUsed", s.segmentUsed); put("used", s.used); put("forfeited", s.forfeited); put("late", s.late)
            put("reason", s.reason?.name ?: ""); put("reasons", JSONArray(s.reasons.map { it.name }))
            put("cooldownUntil", s.cooldownUntil); put("cooldownCause", s.cooldownCause)
            put("entry", s.entry); put("sequence", s.sequence); put("shownPrompt", s.shownPrompt); put("reminders", s.reminders)
            put("homeSerial", s.homeSerial); put("homeHandled", s.homeHandled)
            put("homeSuccesses", s.homeSuccesses); put("homeFailures", s.homeFailures)
            put("gaps", s.gaps); put("unknownMs", s.unknownMs); put("endReason", s.endReason)
            s.prompt?.let { put("prompt", JSONObject().put("id", it.id).put("scene", it.scene).put("lineId", it.lineId)) }
            // Foreground identity is ephemeral; never save non-target activity or reuse it as proof.
        })
    }.toString()
    fun read(text: String): Stored {
        val j = JSONObject(text); val version = j.getInt("schemaVersion")
        require(version in 1..2) { "记录版本不兼容" }
        val s = j.getJSONObject("record")
        val p = s.optJSONObject("prompt")
        val record = Snapshot(config = config(s.getJSONObject("config")),
            status = SessionStatus.valueOf(s.getString("status")), phase = Phase.valueOf(s.getString("phase")),
            sessionId = s.getString("sessionId"), bootId = s.getInt("bootId"), startAt = s.getLong("startAt"), deadline = s.getLong("deadline"),
            round = s.getInt("round"), granted = s.getLong("granted"), grants = s.getInt("grants"), totalGrants = s.getInt("totalGrants"), remaining = s.getLong("remaining"),
            segmentUsed = s.getLong("segmentUsed"), used = s.getLong("used"), forfeited = s.getLong("forfeited"), late = s.getLong("late"),
            reason = s.getString("reason").takeIf { it.isNotEmpty() }?.let { Reason.valueOf(it) },
            reasons = s.getJSONArray("reasons").let { a -> (0 until a.length()).map { Reason.valueOf(a.getString(it)) } },
            cooldownUntil = s.getLong("cooldownUntil"), cooldownCause = s.getString("cooldownCause"),
            entry = s.getLong("entry"), sequence = s.getLong("sequence"), prompt = p?.let { Prompt(it.getString("id"), it.getString("scene"), it.getString("lineId")) },
            shownPrompt = s.getString("shownPrompt"), reminders = s.getInt("reminders"),
            homeSerial = s.getLong("homeSerial"), homeHandled = s.getLong("homeHandled"), homeSuccesses = s.getInt("homeSuccesses"), homeFailures = s.getInt("homeFailures"),
            gaps = s.getInt("gaps"), unknownMs = s.getLong("unknownMs"), endReason = s.getString("endReason"))
        require(record.remaining >= 0 && record.granted >= 0 && record.grants >= 0 && record.used >= 0 && record.round > 0)
        if (record.status in setOf(SessionStatus.ACTIVE, SessionStatus.INTERRUPTED)) {
            val validationConfig = if (version == 1 && record.config.grantMs in 1..3_600_000L)
                record.config.copy(grantMs = record.config.grantMs.coerceAtMost(300_000)) else record.config
            require(validationConfig.error() == null && record.deadline > record.startAt && record.sessionId.isNotBlank())
            require(record.remaining <= record.config.grantMs && record.granted <= record.config.budgetMs && record.grants <= record.config.maxGrants)
        }
        val oldSettings = config(j.getJSONObject("settings"))
        val settings = if (version == 1) oldSettings.copy(
            grantMs = if (oldSettings.grantMs == 300_000L) 120_000L else oldSettings.grantMs.coerceAtMost(300_000L),
            budgetMs = if (oldSettings.grantMs == 300_000L && oldSettings.budgetMs == 900_000L) 360_000L else oldSettings.budgetMs
        ) else oldSettings
        val migrated = if (record.status in setOf(SessionStatus.ACTIVE, SessionStatus.INTERRUPTED) && record.config.grantMs > 300_000)
            record.copy(status = SessionStatus.ENDED, prompt = null, endReason = "旧版单次额度超过新上限，本次监督已结束，统计已保留。请按新设置重新开始。") else record
        return Stored(settings, j.getBoolean("consent"), migrated)
    }
}
