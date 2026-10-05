package org.potato.supervisor.rules

import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private val cfg = Config(packages = setOf("target.a", "target.b"), sessionMs = 120_000,
        grantMs = 5_000, budgetMs = 15_000, cooldownMs = 20_000)
    private fun fresh(config: Config = cfg): RuleEngine = RuleEngine().apply {
        start(config, 0, 7); observe(Observation.TARGET, "target.a", true, 0); shown(state.prompt!!.id, 0)
    }
    private fun click(e: RuleEngine, choice: Choice, at: Long) {
        assertTrue(e.choose(e.state.prompt!!.id, choice, at))
    }
    private fun consume(e: RuleEngine, at: Long) { e.advance(at); e.state.prompt?.let { e.shown(it.id, at) } }

    @Test fun thirdGrantIsUsableAndFourthIsRejected() {
        val e = fresh(); click(e, Choice.REST, 0); consume(e, 5_000)
        click(e, Choice.STUDY, 5_000); consume(e, 10_000)
        click(e, Choice.REST, 10_000)
        assertEquals(Phase.ALLOWANCE, e.state.phase); assertEquals(5_000L, e.state.remaining)
        consume(e, 15_000)
        assertEquals(Phase.COOLDOWN, e.state.phase); assertEquals(15_000L, e.state.used)
        assertFalse(e.choose(e.state.prompt!!.id, Choice.REST, 15_000))
    }
    @Test fun switchingAndReentryRetainSharedBalance() {
        val e = fresh(); click(e, Choice.REST, 0)
        e.observe(Observation.OTHER, "", false, 2_000); e.advance(12_000)
        e.observe(Observation.TARGET, "target.b", true, 12_000); e.shown(e.state.prompt!!.id, 12_000)
        assertEquals(3_000L, e.state.remaining); assertEquals(1, e.state.grants)
        click(e, Choice.CONTINUE, 12_000); consume(e, 15_000)
        assertEquals(5_000L, e.state.used); assertEquals(Phase.GATE, e.state.phase)
    }
    @Test fun explicitReturnForfeitsButDoesNotInventUsage() {
        val e = fresh(); click(e, Choice.REST, 0)
        e.observe(Observation.OTHER, "", false, 1_000)
        e.observe(Observation.TARGET, "target.a", true, 1_000); e.shown(e.state.prompt!!.id, 1_000)
        click(e, Choice.RETURN, 1_000)
        assertEquals(1_000L, e.state.used); assertEquals(4_000L, e.state.forfeited)
        assertEquals(5_000L, e.state.granted); assertEquals(1, e.state.grants)
    }
    @Test fun partialLastGrantHonorsBudget() {
        val e = fresh(cfg.copy(budgetMs = 12_000))
        click(e, Choice.REST, 0); consume(e, 5_000)
        click(e, Choice.REST, 5_000); consume(e, 10_000)
        click(e, Choice.REST, 10_000); assertEquals(2_000L, e.state.remaining)
        consume(e, 12_000); assertEquals(Phase.COOLDOWN, e.state.phase)
    }
    @Test fun countCanEndBeforeBudget() {
        val e = fresh(cfg.copy(budgetMs = 20_000))
        for (i in 0..2) { click(e, Choice.REST, i * 5_000L); consume(e, (i+1) * 5_000L) }
        assertEquals("放行次数用完", e.state.cooldownCause); assertEquals(15_000L, e.state.granted)
    }
    @Test fun promptReadingDoesNotConsumeAllowance() {
        val e = fresh(); click(e, Choice.REST, 0)
        e.observe(Observation.TARGET, "target.b", true, 1_000); e.shown(e.state.prompt!!.id, 1_000)
        e.advance(11_000)
        assertEquals(1_000L, e.state.used); assertEquals(4_000L, e.state.remaining)
    }
    @Test fun screenLockPausesUsageButNotSession() {
        val e = fresh(); click(e, Choice.REST, 0)
        e.observe(Observation.LOCKED, "", false, 1_000); e.advance(120_000)
        assertEquals(1_000L, e.state.used); assertEquals(SessionStatus.ENDED, e.state.status)
    }
    @Test fun unknownRequiresNewEvidenceAndIsRecorded() {
        val e = fresh(); click(e, Choice.STUDY, 0)
        e.observe(Observation.UNKNOWN, "", false, 1_000); e.advance(4_000)
        assertEquals(4_000L, e.state.remaining); assertEquals(3_000L, e.state.unknownMs)
        e.observe(Observation.TARGET, "target.a", false, 4_000); consume(e, 8_000)
        assertEquals(5_000L, e.state.used)
    }
    @Test fun duplicateButtonCannotGrantTwice() {
        val e = fresh(); val id = e.state.prompt!!.id
        assertTrue(e.choose(id, Choice.REST, 0)); assertFalse(e.choose(id, Choice.STUDY, 0))
        assertEquals(1, e.state.grants)
    }
    @Test fun internalEventsDoNotCreateNewPrompt() {
        val e = fresh(); val id = e.state.prompt!!.id
        repeat(10) { e.observe(Observation.TARGET, "target.a", false, 0); e.shown(id, 0) }
        assertEquals(id, e.state.prompt!!.id); assertEquals(1, e.state.reminders)
    }
    @Test fun lateTickSeparatesOvershootAndAnchorsCooldown() {
        val e = fresh(cfg.copy(maxGrants = 1)); click(e, Choice.REST, 0); consume(e, 8_000)
        assertEquals(3_000L, e.state.late); assertEquals(8_000L, e.state.used)
        assertEquals(25_000L, e.state.cooldownUntil); assertEquals(0L, e.state.remaining)
    }
    @Test fun cooldownEntryDoesNotExtendDeadline() {
        val e = fresh(cfg.copy(maxGrants = 1)); click(e, Choice.REST, 0); consume(e, 5_000)
        e.takeHome(5_000); e.homeResult(true)
        repeat(3) { i ->
            e.observe(Observation.OTHER, "", false, 6_000+i*1_000L)
            e.observe(Observation.TARGET, "target.a", true, 6_000+i*1_000L)
            assertNotNull(e.takeHome(6_000+i*1_000L)); assertNull(e.takeHome(6_000+i*1_000L))
            assertEquals(25_000L, e.state.cooldownUntil)
        }
    }
    @Test fun cooldownResetsOneRoundEvenWithLongGap() {
        val e = fresh(cfg.copy(maxGrants = 1)); click(e, Choice.REST, 0); consume(e, 5_000)
        e.observe(Observation.OTHER, "", false, 5_000); e.advance(90_000)
        assertEquals(2, e.state.round); assertEquals(0, e.state.grants); assertEquals(Phase.GATE, e.state.phase)
    }
    @Test fun remindModeContinuesWithoutFourthGrantOrHome() {
        val e = fresh(cfg.copy(mode = Mode.REMIND, maxGrants = 1))
        click(e, Choice.REST, 0); consume(e, 5_000)
        assertEquals(Phase.REMINDER, e.state.phase); assertNull(e.takeHome(5_000))
        click(e, Choice.CONTINUE, 5_000); consume(e, 10_000)
        assertEquals(1, e.state.grants); assertEquals("over_limit", e.state.prompt!!.scene)
    }
    @Test fun remindModeReentryDoesNotRefreshInterval() {
        val e = fresh(cfg.copy(mode = Mode.REMIND, maxGrants = 1)); click(e, Choice.STUDY, 0); consume(e, 5_000)
        click(e, Choice.CONTINUE, 5_000); e.observe(Observation.OTHER, "", false, 7_000)
        e.observe(Observation.TARGET, "target.b", true, 9_000); e.shown(e.state.prompt!!.id, 9_000)
        click(e, Choice.CONTINUE, 9_000); assertEquals(3_000L, e.state.remaining)
    }
    @Test fun sessionDeadlineWinsOverExtension() {
        val e = fresh(cfg.copy(sessionMs = 5_000)); click(e, Choice.REST, 0); consume(e, 5_000)
        assertEquals(SessionStatus.ENDED, e.state.status); assertNull(e.state.prompt); assertNull(e.takeHome(5_000))
    }
    @Test fun savedBalanceRestoresWithoutNewGrant() {
        val e = fresh(); click(e, Choice.REST, 0); e.advance(1_000); e.interrupt("服务中断", 1_000)
        val restored = RuleEngine(); restored.replace(e.state, 10_000); restored.recover(10_000, 7)
        assertEquals(1, restored.state.grants); assertEquals(4_000L, restored.state.remaining)
        assertEquals(Observation.UNKNOWN, restored.state.observation); assertEquals(1_000L, restored.state.used)
    }
    @Test fun rebootEndsOldSession() {
        val e = fresh(); e.interrupt("进程中断", 0); e.recover(0, 8)
        assertEquals(SessionStatus.ENDED, e.state.status); assertEquals("重启导致监测中断", e.state.endReason)
    }
    @Test fun interruptedSessionStillExpires() {
        val e = fresh(); e.interrupt("服务中断", 0); e.recover(120_000, 7)
        assertEquals(SessionStatus.ENDED, e.state.status)
    }
    @Test fun faultClearsOverlayAndHomeRequests() {
        val e = fresh(); e.interrupt("权限被撤销", 0)
        assertFalse(e.state.shouldShow()); assertNull(e.takeHome(0)); assertEquals(SessionStatus.INTERRUPTED, e.state.status)
    }
    @Test fun stalePromptFromOldSessionIsRejected() {
        val e = fresh(); val old = e.state.prompt!!.id; e.end("主动结束"); e.start(cfg, 1_000, 7)
        e.observe(Observation.TARGET, "target.a", true, 1_000)
        assertFalse(e.choose(old, Choice.REST, 1_000)); assertEquals(0, e.state.grants)
    }
    @Test fun invalidConfigurationIsRejected() {
        assertNotNull(cfg.copy(packages = emptySet()).error()); assertNotNull(cfg.copy(grantMs = 0).error())
        assertNotNull(cfg.copy(sessionMs = Long.MAX_VALUE).error())
    }
    @Test fun partialTimeIsDisplayedWithoutInventingMinutes() {
        assertEquals("20秒", duration(19_001)); assertEquals("1分1秒", duration(60_001))
    }
}
