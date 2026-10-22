package com.parentcontrol.socialblocker.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class SectionTimerTest {
    private val SEC = 1_000L
    private val MIN = 60_000L
    private val DAY_MS = 86_400_000L
    private val clock = LocalDayClock(TimeZone.getTimeZone("UTC"))
    /** 2026-10-22 00:00 UTC, a Thursday. */
    private val DAY = 1_792_627_200_000L
    private val commits = ArrayList<Triple<Int, String, Long>>()
    private val timer = SectionTimer(clock, graceMs = SEC) { d, k, ms -> commits += Triple(d, k, ms) }
    private val A = "com.whatsapp/updates"
    private val B = "com.lemon.lvoverseas/templates"

    @Test
    fun clockKnowsTheDay() {
        assertEquals(20261022, clock.dayKey(DAY + 5 * MIN))
        assertEquals(DAY, clock.dayStart(DAY + 5 * MIN))
        assertEquals(DAY + DAY_MS, clock.nextMidnight(DAY + 5 * MIN))
        assertFalse(clock.isWeekend(DAY))
        assertTrue(clock.isWeekend(DAY + 2 * DAY_MS))   // Saturday
        assertTrue(clock.isWeekend(DAY + 3 * DAY_MS))   // Sunday
        assertFalse(clock.isWeekend(DAY + 4 * DAY_MS))  // Monday
    }

    @Test
    fun nothingCountsBeforeASection() {
        timer.observe(null, DAY)
        timer.observe(null, DAY + MIN)
        assertEquals(0L, timer.spentToday(A, DAY + MIN))
        assertFalse(timer.active)
    }

    @Test
    fun timeRunsFromTheFirstObservation() {
        timer.observe(A, DAY + 10 * MIN)
        assertEquals(A, timer.current)
        assertEquals(3 * MIN, timer.spentToday(A, DAY + 13 * MIN))
        assertTrue(timer.active)
    }

    @Test
    fun leavingIsConfirmedAfterTheGraceAndCountsUpToTheFirstAbsence() {
        timer.observe(A, DAY + 10 * MIN)
        timer.observe(null, DAY + 15 * MIN)                 // first seen away: provisional exit
        assertEquals(A, timer.current)                       // not confirmed yet
        assertEquals(5 * MIN, timer.spentToday(A, DAY + 15 * MIN + 500))
        timer.observe(null, DAY + 15 * MIN + SEC)           // a second later, still away: confirmed
        assertNull(timer.current)
        assertFalse(timer.active)
        assertEquals(5 * MIN, timer.spentToday(A, DAY + 20 * MIN))
        assertEquals(listOf(Triple(20261022, A, 5 * MIN)), commits)
    }

    @Test
    fun aFlickerWithinTheGraceIsNothing() {
        timer.observe(A, DAY + 10 * MIN)
        timer.observe(null, DAY + 12 * MIN)                 // the window blinks
        timer.observe(A, DAY + 12 * MIN + 400)              // and A is back before a second passed
        assertEquals(A, timer.current)
        timer.observe(A, DAY + 20 * MIN)
        assertEquals(10 * MIN, timer.spentToday(A, DAY + 20 * MIN))
        assertTrue(commits.isEmpty())
    }

    @Test
    fun aFlickerToAnotherSectionIsNothingEither() {
        timer.observe(A, DAY + 10 * MIN)
        timer.observe(B, DAY + 12 * MIN)
        timer.observe(A, DAY + 12 * MIN + 900)
        assertEquals(A, timer.current)
        assertEquals(0L, timer.spentToday(B, DAY + 13 * MIN))
        assertEquals(3 * MIN, timer.spentToday(A, DAY + 13 * MIN))
    }

    @Test
    fun aSwitchOfSectionStartsTheNewOneWhenItWasFirstSeen() {
        timer.observe(A, DAY + 10 * MIN)
        timer.observe(B, DAY + 15 * MIN)
        timer.observe(B, DAY + 15 * MIN + SEC)
        assertEquals(B, timer.current)
        assertEquals(5 * MIN, timer.spentToday(A, DAY + 20 * MIN))
        assertEquals(5 * MIN, timer.spentToday(B, DAY + 20 * MIN))
    }

    @Test
    fun theCandidateMustStayTheSameToBeConfirmed() {
        timer.observe(A, DAY + 10 * MIN)
        timer.observe(null, DAY + 15 * MIN)
        timer.observe(B, DAY + 15 * MIN + 600)              // something else again: the grace restarts
        timer.observe(B, DAY + 15 * MIN + 900)              // 300 ms of B: not yet
        assertEquals(A, timer.current)
        timer.observe(B, DAY + 15 * MIN + 1_700)            // 1.1 s of B: confirmed, A closed at B's first sight
        assertEquals(B, timer.current)
        assertEquals(5 * MIN + 600, timer.spentToday(A, DAY + 20 * MIN))
    }

    @Test
    fun screenOffClosesAtOnce() {
        timer.observe(A, DAY + 10 * MIN)
        timer.screenOff(DAY + 12 * MIN)
        assertNull(timer.current)
        assertFalse(timer.active)
        assertEquals(2 * MIN, timer.spentToday(A, DAY + 30 * MIN))
        timer.observe(A, DAY + 30 * MIN)                    // the phone wakes up on the same screen
        assertEquals(2 * MIN + 1 * MIN, timer.spentToday(A, DAY + 31 * MIN))
    }

    @Test
    fun midnightSplitsTheStretchAndResetsTheDay() {
        val midnight = DAY + DAY_MS
        timer.observe(A, midnight - 10 * MIN)
        assertEquals(10 * MIN - 1, timer.spentToday(A, midnight - 1))
        // after midnight, only the part since midnight is today's
        assertEquals(3 * MIN, timer.spentToday(A, midnight + 3 * MIN))
        timer.observe(null, midnight + 5 * MIN)
        timer.observe(null, midnight + 5 * MIN + SEC)
        assertEquals(5 * MIN, timer.spentToday(A, midnight + 6 * MIN))
        assertEquals(listOf(Triple(20261022, A, 10 * MIN), Triple(20261023, A, 5 * MIN)), commits)
    }

    @Test
    fun totalsAccumulateAcrossStretches() {
        timer.observe(A, DAY + 10 * MIN); timer.observe(null, DAY + 12 * MIN); timer.observe(null, DAY + 12 * MIN + SEC)
        timer.observe(A, DAY + 20 * MIN); timer.observe(null, DAY + 23 * MIN); timer.observe(null, DAY + 23 * MIN + SEC)
        assertEquals(5 * MIN, timer.spentToday(A, DAY + 30 * MIN))
        assertEquals(5 * MIN, commits.last().third)
    }

    @Test
    fun seedRestoresTheDaySTotal() {
        timer.seed(20261022, mapOf(A to 7 * MIN))
        timer.observe(A, DAY + 10 * MIN)
        assertEquals(9 * MIN, timer.spentToday(A, DAY + 12 * MIN))
    }

    @Test
    fun yesterdaysSeedCountsForNothingToday() {
        timer.seed(20261021, mapOf(A to 7 * MIN))
        assertEquals(0L, timer.spentToday(A, DAY + MIN))
    }
}
