package com.parentcontrol.socialblocker.sections

/**
 * The quota of one section: minutes a day, one figure for the week and one for the weekend.
 * An enabled section with 0 minutes is closed all day. Pure, tested from the JVM.
 */
data class Quota(val enabled: Boolean = false, val weekdayMinutes: Int = 30, val weekendMinutes: Int = 60) {
    companion object {
        const val WARN_MS = 5 * 60_000L
    }

    fun limitMs(weekend: Boolean): Long = (if (weekend) weekendMinutes else weekdayMinutes) * 60_000L

    /** True when the section must be left: enabled and today's minutes spent. */
    fun exhausted(spentMs: Long, weekend: Boolean): Boolean = enabled && spentMs >= limitMs(weekend)

    /** The whole minutes left today, 0 once spent. */
    fun minutesLeft(spentMs: Long, weekend: Boolean): Int {
        val left = limitMs(weekend) - spentMs
        return if (left <= 0) 0 else ((left + 59_999L) / 60_000L).toInt()
    }

    /** True inside the last five minutes: the warning is due (once a day, the caller remembers). */
    fun warnDue(spentMs: Long, weekend: Boolean): Boolean {
        val limit = limitMs(weekend)
        return enabled && limit > WARN_MS && spentMs >= limit - WARN_MS && spentMs < limit
    }
}

/**
 * Keeps the quota's way out from looping: at most [maxActions] taps or backs per [windowMs],
 * then nothing but the message for [cooldownMs]. WhatsApp may put the section back in front
 * after a back, or the escape node may not do what the rules expect; without this the
 * service would fight the app for ever.
 */
class ActionGuard(private val maxActions: Int = 3, private val windowMs: Long = 10_000L, private val cooldownMs: Long = 30_000L) {
    private val recent = ArrayList<Long>()
    private var coolUntil = 0L

    /** True when an action may be taken now; counts it. */
    fun allow(now: Long): Boolean {
        if (now < coolUntil) return false
        recent.removeAll { now - it >= windowMs }
        if (recent.size >= maxActions) { coolUntil = now + cooldownMs; recent.clear(); return false }
        recent += now
        return true
    }

    fun cooling(now: Long): Boolean = now < coolUntil
}
