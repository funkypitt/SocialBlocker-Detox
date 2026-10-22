package com.parentcontrol.socialblocker.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaTest {
    private val MIN = 60_000L
    private val q = Quota(enabled = true, weekdayMinutes = 30, weekendMinutes = 60)

    @Test
    fun weekAndWeekendHaveTheirOwnLimit() {
        assertEquals(30 * MIN, q.limitMs(false))
        assertEquals(60 * MIN, q.limitMs(true))
        assertTrue(q.exhausted(30 * MIN, false))
        assertFalse(q.exhausted(30 * MIN, true))
        assertTrue(q.exhausted(60 * MIN, true))
    }

    @Test
    fun aDisabledSectionIsNeverExhausted() {
        assertFalse(q.copy(enabled = false).exhausted(1000 * MIN, false))
        assertFalse(q.copy(enabled = false).warnDue(26 * MIN, false))
    }

    @Test
    fun zeroMinutesMeansClosedAllDay() {
        assertTrue(q.copy(weekdayMinutes = 0).exhausted(0, false))
        assertFalse(q.copy(weekdayMinutes = 0).warnDue(0, false))
    }

    @Test
    fun minutesLeftRoundUp() {
        assertEquals(30, q.minutesLeft(0, false))
        assertEquals(30, q.minutesLeft(1, false))
        assertEquals(29, q.minutesLeft(MIN, false))
        assertEquals(1, q.minutesLeft(30 * MIN - 1, false))
        assertEquals(0, q.minutesLeft(30 * MIN, false))
        assertEquals(0, q.minutesLeft(45 * MIN, false))
    }

    @Test
    fun warningInTheLastFiveMinutes() {
        assertFalse(q.warnDue(24 * MIN, false))
        assertTrue(q.warnDue(25 * MIN, false))
        assertTrue(q.warnDue(29 * MIN, false))
        assertFalse(q.warnDue(30 * MIN, false))
        // a quota of five minutes or less has no warning
        assertFalse(q.copy(weekdayMinutes = 5).warnDue(1 * MIN, false))
    }

    @Test
    fun guardAllowsThreeThenCoolsDown() {
        val g = ActionGuard(maxActions = 3, windowMs = 10_000L, cooldownMs = 30_000L)
        assertTrue(g.allow(0)); assertTrue(g.allow(1000)); assertTrue(g.allow(2000))
        assertFalse(g.allow(3000))                 // fourth within 10 s: cool-down begins
        assertTrue(g.cooling(20_000L))
        assertFalse(g.allow(32_000L))              // still cooling (3 s + 30 s)
        assertTrue(g.allow(33_100L))
    }

    @Test
    fun guardForgetsOldActions() {
        val g = ActionGuard(maxActions = 3, windowMs = 10_000L, cooldownMs = 30_000L)
        assertTrue(g.allow(0)); assertTrue(g.allow(1000)); assertTrue(g.allow(2000))
        assertTrue(g.allow(10_500L))               // the first one is older than the window
        assertTrue(g.allow(11_500L))
        assertFalse(g.allow(11_600L))              // 2000, 10500, 11500 within the window
    }
}
