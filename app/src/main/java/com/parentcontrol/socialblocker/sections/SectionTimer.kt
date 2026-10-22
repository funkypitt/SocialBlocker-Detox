package com.parentcontrol.socialblocker.sections

import com.parentcontrol.socialblocker.Budget
import java.util.Calendar
import java.util.TimeZone

/** The local calendar, as the timer sees it: days begin at midnight, phone time. */
interface DayClock {
    fun dayKey(t: Long): Int
    fun dayStart(t: Long): Long
    fun nextMidnight(t: Long): Long
    fun isWeekend(t: Long): Boolean
}

class LocalDayClock(private val zone: TimeZone = TimeZone.getDefault()) : DayClock {
    private fun cal(t: Long): Calendar = Calendar.getInstance(zone).apply { timeInMillis = t }
    override fun dayKey(t: Long) = Budget.dayKey(cal(t))
    override fun dayStart(t: Long) = Budget.dayStart(cal(t))
    override fun nextMidnight(t: Long) = Budget.nextMidnight(cal(t))
    override fun isWeekend(t: Long) = cal(t).get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
}

/**
 * The stopwatch of the sections. It is told what is in front at each observation — a section
 * key, or null for anything else — and the time, and keeps the day's total of each section.
 *
 * Time runs from the first observation of a section (nothing to hesitate about when nothing
 * was in front) to the first observation of something else, provided that observation is
 * confirmed: the section must stay away for [graceMs]. If it is seen again within the grace
 * (a tab animation, a dialog, a flicker of the window), nothing happened and the time runs on.
 * A switch from one section to another is confirmed the same way, and the new section's time
 * then starts at the moment it was first seen. The screen going off closes at once.
 *
 * A stretch across midnight is split between the two days. Nothing here touches Android: the
 * service feeds it and persists what [onCommit] reports (see SectionTimerTest).
 */
class SectionTimer(
    private val clock: DayClock,
    private val graceMs: Long = 1_000L,
    /** Called with the day, the section and the day's committed total each time a stretch closes. */
    private val onCommit: (dayKey: Int, key: String, totalMs: Long) -> Unit = { _, _, _ -> }
) {
    private val committed = HashMap<Int, HashMap<String, Long>>()

    /** The section whose time is running, from [enteredAt]. */
    var current: String? = null
        private set
    private var enteredAt = 0L

    /** What was last seen instead of [current], since when; cleared when [current] comes back. */
    private var candidate: String? = null
    private var candidateSince = 0L
    private var hasCandidate = false

    /** True while a stretch is open or a change is waiting to be confirmed: the service must tick. */
    val active: Boolean get() = current != null || hasCandidate

    /** Today's totals as the store remembered them, loaded once at start. */
    fun seed(dayKey: Int, totals: Map<String, Long>) {
        committed.getOrPut(dayKey) { HashMap() }.putAll(totals)
    }

    /** One look at the screen: [section] is in front (null = none) at [now]. */
    fun observe(section: String?, now: Long) {
        if (section == current) { hasCandidate = false; candidate = null; return }
        if (current == null) {
            // nothing was running: the new section starts at once
            current = section; enteredAt = now
            hasCandidate = false; candidate = null
            return
        }
        if (hasCandidate && candidate == section) {
            if (now - candidateSince >= graceMs) {
                close(candidateSince)
                current = section
                enteredAt = candidateSince
                hasCandidate = false; candidate = null
            }
            return
        }
        candidate = section; candidateSince = now; hasCandidate = true
    }

    /** The screen went off (or the service stops): whatever runs stops now, nothing to confirm. */
    fun screenOff(now: Long) {
        if (current != null) close(now)
        current = null
        hasCandidate = false; candidate = null
    }

    /** Milliseconds of [key] today, the open stretch included up to the moment it was last seen. */
    fun spentToday(key: String, now: Long): Long {
        val day = clock.dayKey(now)
        var total = committed[day]?.get(key) ?: 0L
        if (current == key) {
            val end = if (hasCandidate) candidateSince else now
            val start = maxOf(enteredAt, clock.dayStart(now))
            if (end > start) total += end - start
        }
        return total
    }

    private fun close(end: Long) {
        val key = current ?: return
        var start = enteredAt
        while (start < end) {
            val midnight = clock.nextMidnight(start)
            val stop = minOf(end, midnight)
            val day = clock.dayKey(start)
            val m = committed.getOrPut(day) { HashMap() }
            val total = (m[key] ?: 0L) + (stop - start)
            m[key] = total
            onCommit(day, key, total)
            start = stop
        }
        // the days before yesterday are of no use any more
        val keep = setOf(clock.dayKey(end), clock.dayKey(clock.dayStart(end) - 1))
        committed.keys.filter { it !in keep }.forEach { committed.remove(it) }
    }
}
