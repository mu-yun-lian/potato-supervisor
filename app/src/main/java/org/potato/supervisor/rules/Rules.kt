package org.potato.supervisor.rules

import java.util.UUID
import kotlin.math.min

enum class SessionStatus { OFF, ACTIVE, INTERRUPTED, ENDED }
enum class Phase { GATE, ALLOWANCE, COOLDOWN, REMINDER }
enum class Observation { TARGET, OTHER, LOCKED, UNKNOWN }
enum class Mode { LIMIT, REMIND }
enum class Reason { STUDY, REST }
enum class Choice { RETURN, STUDY, REST, CONTINUE, END }

data class Config(
    val goal: String = "完成今天的学习计划",
    val task: String = "先完成一道题",
    val note: String = "",
    val packages: Set<String> = emptySet(),
    val sessionMs: Long = 60 * 60_000L,
    val grantMs: Long = 2 * 60_000L,
    val maxGrants: Int = 3,
    val budgetMs: Long = 6 * 60_000L,
    val cooldownMs: Long = 20 * 60_000L,
    val mode: Mode = Mode.LIMIT,
    val tone: String = "snark",
    val vibration: Boolean = false,
    val reducedMotion: Boolean = false,
    val sound: Boolean = false,
    val soundVolume: Int = 35
) {
    fun error(): String? = when {
        goal.isBlank() || task.isBlank() -> "请先填写学习目标和本次任务"
        goal.length > 200 || task.length > 500 || note.length > 2000 -> "目标最多 200 字，任务 500 字，寄语 2000 字"
        packages.isEmpty() -> "请至少选择一个需要监督的应用"
        grantMs !in 1..300_000L -> "单次放行最多 5 分钟，请填写 1–300 秒"
        sessionMs !in 1..43_200_000L ||
            budgetMs !in 1..36_000_000L || cooldownMs !in 1..43_200_000L || maxGrants !in 1..20 -> "时间或次数超出允许范围"
        soundVolume !in 0..100 -> "音效音量应为 0–100"
        tone !in setOf("gentle", "snark", "roast") -> "请选择有效语气"
        else -> null
    }
}

data class Prompt(val id: String, val scene: String, val lineId: String = "")
data class Snapshot(
    val config: Config = Config(),
    val status: SessionStatus = SessionStatus.OFF,
    val phase: Phase = Phase.GATE,
    val sessionId: String = "",
    val bootId: Int = -1,
    val startAt: Long = 0,
    val deadline: Long = 0,
    val round: Int = 1,
    val granted: Long = 0,
    val grants: Int = 0,
    val totalGrants: Int = 0,
    val remaining: Long = 0,
    val segmentUsed: Long = 0,
    val used: Long = 0,
    val forfeited: Long = 0,
    val late: Long = 0,
    val reason: Reason? = null,
    val reasons: List<Reason> = emptyList(),
    val cooldownUntil: Long = 0,
    val cooldownCause: String = "",
    val observation: Observation = Observation.UNKNOWN,
    val targetPackage: String = "",
    val entry: Long = 0,
    val sequence: Long = 0,
    val prompt: Prompt? = null,
    val shownPrompt: String = "",
    val reminders: Int = 0,
    val homeSerial: Long = 0,
    val homeHandled: Long = 0,
    val homeSuccesses: Int = 0,
    val homeFailures: Int = 0,
    val gaps: Int = 0,
    val unknownMs: Long = 0,
    val endReason: String = ""
) {
    fun canGrant() = grants < config.maxGrants && granted < config.budgetMs
    fun nextGrant() = min(config.grantMs, (config.budgetMs - granted).coerceAtLeast(0))
    fun shouldShow() = status == SessionStatus.ACTIVE && observation == Observation.TARGET && prompt != null
}

/** One deterministic rule owner. All methods are called serially; Android is an adapter. */
class RuleEngine(initial: Snapshot = Snapshot()) {
    var state = initial
        private set
    private var accountedAt = initial.startAt
    private var overlayShown = false

    fun replace(snapshot: Snapshot, now: Long) { state = snapshot; accountedAt = now; overlayShown = false }
    fun setLine(id: String) { state = state.copy(prompt = state.prompt?.copy(lineId = id)) }

    fun updatePresentation(c: Config) {
        val changedTone = state.config.tone != c.tone
        state = state.copy(config = state.config.copy(tone=c.tone, vibration=c.vibration,
            reducedMotion=c.reducedMotion, sound=c.sound, soundVolume=c.soundVolume),
            prompt = if(changedTone) state.prompt?.copy(lineId="") else state.prompt)
    }

    fun start(config: Config, now: Long, boot: Int) {
        require(config.error() == null) { config.error() ?: "无效设置" }
        require(state.status != SessionStatus.ACTIVE && state.status != SessionStatus.INTERRUPTED) { "请先结束当前时段" }
        state = Snapshot(config = config, status = SessionStatus.ACTIVE, sessionId = UUID.randomUUID().toString(),
            bootId = boot, startAt = now, deadline = now + config.sessionMs)
        accountedAt = now; overlayShown = false
    }

    fun advance(now: Long) {
        require(now >= accountedAt) { "时钟回退，需要重新开始" }
        if (state.status != SessionStatus.ACTIVE) { accountedAt = now; return }
        val until = min(now, state.deadline)
        val delta = (until - accountedAt).coerceAtLeast(0)
        if (state.observation == Observation.UNKNOWN && delta > 0) {
            state = state.copy(unknownMs = state.unknownMs + delta)
        }
        if (state.observation == Observation.TARGET && !overlayShown && state.remaining > 0 &&
            state.phase in setOf(Phase.ALLOWANCE, Phase.REMINDER)) {
            val consumed = min(delta, state.remaining)
            val finishAt = accountedAt + state.remaining
            val overshoot = delta - consumed
            state = state.copy(remaining = state.remaining - consumed, segmentUsed = state.segmentUsed + consumed,
                used = state.used + delta, late = state.late + overshoot)
            if (state.remaining == 0L && until < state.deadline) {
                if (state.phase == Phase.REMINDER) newPrompt("over_limit")
                else finishSegment(finishAt)
            }
        }
        accountedAt = now
        if (now >= state.deadline) { end("学习时段结束"); return }
        // A late callback first requests an exit. Expiry is handled on the following evaluation.
        if (state.phase == Phase.COOLDOWN && now >= state.cooldownUntil &&
            (state.homeSerial == state.homeHandled || state.observation != Observation.TARGET)) {
            state = state.copy(phase = Phase.GATE, round = state.round + 1, granted = 0, grants = 0,
                remaining = 0, segmentUsed = 0, reason = null, reasons = emptyList(), cooldownUntil = 0,
                cooldownCause = "", prompt = null, homeHandled = state.homeSerial)
            if (state.observation == Observation.TARGET) newPrompt("entry")
        }
    }

    fun observe(observation: Observation, target: String, realEntry: Boolean, now: Long) {
        advance(now)
        if (state.status != SessionStatus.ACTIVE) return
        val prior = state.observation
        state = state.copy(observation = observation, targetPackage = if (observation == Observation.TARGET) target else "")
        if (observation == Observation.UNKNOWN && prior != Observation.UNKNOWN) state = state.copy(gaps = state.gaps + 1)
        if (observation != Observation.TARGET) { overlayShown = false; return }
        if (realEntry) state = state.copy(entry = state.entry + 1)
        when {
            state.phase == Phase.COOLDOWN -> {
                if (realEntry || state.prompt == null) { newPrompt("cooldown"); requestHome() }
            }
            state.remaining > 0 -> if (realEntry) newPrompt("reentry")
            state.phase == Phase.REMINDER -> if (realEntry || state.prompt == null) newPrompt("over_limit")
            state.phase == Phase.GATE -> if (realEntry || state.prompt == null) {
                if (state.canGrant()) newPrompt(if (state.grants == 0) "entry" else followup())
                else exhaust(now)
            }
        }
    }

    fun shown(promptId: String, now: Long) {
        advance(now)
        if (!state.shouldShow() || state.prompt?.id != promptId) return
        overlayShown = true
        if (state.shownPrompt != promptId) state = state.copy(shownPrompt = promptId, reminders = state.reminders + 1)
    }
    fun hidden(now: Long) { advance(now); overlayShown = false }

    fun choose(promptId: String, choice: Choice, now: Long): Boolean {
        advance(now)
        if (!state.shouldShow() || state.prompt?.id != promptId) return false
        when (choice) {
            Choice.END -> end("紧急解除")
            Choice.RETURN -> {
                state = state.copy(forfeited = state.forfeited + if (state.phase == Phase.ALLOWANCE) state.remaining else 0,
                    remaining = 0, prompt = null)
                if (state.phase == Phase.ALLOWANCE) {
                    state = state.copy(phase = Phase.GATE)
                    if (!state.canGrant()) exhaust(now)
                }
                requestHome()
            }
            Choice.STUDY, Choice.REST -> {
                if (state.phase != Phase.GATE || !state.canGrant() || state.remaining > 0) return false
                val reason = if (choice == Choice.STUDY) Reason.STUDY else Reason.REST
                val duration = state.nextGrant()
                state = state.copy(phase = Phase.ALLOWANCE, remaining = duration, segmentUsed = 0,
                    granted = state.granted + duration, grants = state.grants + 1, totalGrants = state.totalGrants + 1, reason = reason,
                    reasons = state.reasons + reason, prompt = null)
            }
            Choice.CONTINUE -> {
                if (state.phase !in setOf(Phase.ALLOWANCE, Phase.REMINDER)) return false
                if (state.phase == Phase.ALLOWANCE && state.remaining == 0L) return false
                state = state.copy(remaining = if (state.remaining > 0) state.remaining else state.config.grantMs,
                    prompt = null)
            }
        }
        overlayShown = false; accountedAt = now
        return true
    }

    fun interrupt(message: String, now: Long) {
        advance(now)
        if (state.status == SessionStatus.ACTIVE) state = state.copy(status = SessionStatus.INTERRUPTED,
            observation = Observation.UNKNOWN, targetPackage = "", prompt = null, gaps = state.gaps + 1, endReason = message)
        overlayShown = false
    }

    fun recover(now: Long, boot: Int) {
        if (state.status != SessionStatus.INTERRUPTED) return
        if (boot != state.bootId) { end("重启导致监测中断"); accountedAt = now; return }
        if (now >= state.deadline) { end("学习时段结束（监测曾中断）"); accountedAt = now; return }
        state = state.copy(status = SessionStatus.ACTIVE, observation = Observation.UNKNOWN, targetPackage = "", prompt = null, endReason = "")
        accountedAt = now; overlayShown = false
        if (state.phase == Phase.COOLDOWN && now >= state.cooldownUntil) {
            state = state.copy(homeHandled = state.homeSerial)
            advance(now)
        }
    }
    fun end(message: String) {
        state = state.copy(status = SessionStatus.ENDED, observation = Observation.UNKNOWN, targetPackage = "",
            prompt = null, homeHandled = state.homeSerial, endReason = message)
        overlayShown = false
    }
    fun takeHome(now: Long): Long? {
        advance(now)
        if (state.status != SessionStatus.ACTIVE || state.observation != Observation.TARGET || state.homeSerial == state.homeHandled) return null
        val id = state.homeSerial
        state = state.copy(homeHandled = id)
        return id
    }
    fun homeResult(success: Boolean) {
        state = if (success) state.copy(homeSuccesses = state.homeSuccesses + 1)
        else state.copy(homeFailures = state.homeFailures + 1)
    }
    private fun followup() = if (state.reason == Reason.STUDY) "followup_study" else "followup_rest"
    private fun finishSegment(now: Long) {
        state = state.copy(phase = Phase.GATE, prompt = null)
        if (state.canGrant()) newPrompt(followup()) else exhaust(now)
    }
    private fun exhaust(now: Long) {
        if (state.config.mode == Mode.REMIND) {
            state = state.copy(phase = Phase.REMINDER, prompt = null); newPrompt("over_limit")
        } else {
            val cause = listOfNotNull(if (state.grants >= state.config.maxGrants) "放行次数用完" else null,
                if (state.granted >= state.config.budgetMs) "本轮额度用完" else null).joinToString("，")
            state = state.copy(phase = Phase.COOLDOWN, cooldownUntil = now + state.config.cooldownMs, cooldownCause = cause)
            newPrompt("cooldown")
            if (state.observation == Observation.TARGET) requestHome()
        }
    }
    private fun requestHome() { state = state.copy(homeSerial = state.homeSerial + 1) }
    private fun newPrompt(scene: String) {
        val sequence = state.sequence + 1
        state = state.copy(sequence = sequence, prompt = Prompt("${state.sessionId}:${state.round}:$sequence", scene))
    }
}

fun duration(ms: Long): String {
    val seconds = ((ms.coerceAtLeast(0) + 999) / 1000)
    return when {
        seconds < 60 -> "${seconds}秒"
        seconds % 60 == 0L -> "${seconds / 60}分钟"
        else -> "${seconds / 60}分${seconds % 60}秒"
    }
}
