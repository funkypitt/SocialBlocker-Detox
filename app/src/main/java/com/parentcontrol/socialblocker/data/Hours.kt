package com.parentcontrol.socialblocker.data

/**
 * The allowed hours, as [com.parentcontrol.socialblocker.ScheduleChecker] reads them:
 * "6-8,18-22" = open from 6:00 to 8:00 and from 18:00 to 22:00, blocked the rest of the day.
 * An end before the start runs over midnight. Empty = blocked all day.
 */
data class Range(val start: Int, val end: Int) {
    val label: String get() = "$start:00 – $end:00"
    override fun toString() = "$start-$end"
}

object Hours {
    fun parse(schedule: String): List<Range> = schedule.split(",").mapNotNull { part ->
        val p = part.trim().split("-")
        val a = p.getOrNull(0)?.trim()?.toIntOrNull()
        val b = p.getOrNull(1)?.trim()?.toIntOrNull()
        if (p.size == 2 && a != null && b != null && a in 0..23 && b in 0..23) Range(a, b) else null
    }

    fun format(ranges: List<Range>): String = ranges.joinToString(",")

    /** The short form for a secondary line: "6–8, 18–22". */
    fun short(ranges: List<Range>): String = ranges.joinToString(", ") { "${it.start}–${it.end}" }

    /** A typed hour: "18", "18:00", "18h" → 18; anything else → null. */
    fun hourOf(text: String): Int? =
        Regex("^\\s*(\\d{1,2})\\s*(?:[:hH.]\\s*0{0,2})?\\s*$").find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 }
}
