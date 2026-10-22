package com.parentcontrol.socialblocker.sections

import android.content.Context
import android.content.SharedPreferences

/**
 * What the family build remembers about the sections: the parent's PIN, each section's quota,
 * the day's totals (by day key, so a count of another day is simply never read), the warnings
 * already given today, the discovery mode's end. SharedPreferences, like the rest of the app.
 * Nothing in here is about the screens' content: keys name sections, values are minutes.
 */
class SectionPrefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("sections", Context.MODE_PRIVATE)

    // ---- the parent's PIN: the whole app opens on it once set ----

    val pinSet: Boolean get() = sp.getString("pin_hash", null) != null

    fun setPin(pin: String) {
        val salt = Pin.newSalt()
        sp.edit().putString("pin_salt", salt).putString("pin_hash", Pin.hash(pin, salt)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pin_salt", null) ?: return false
        val hash = sp.getString("pin_hash", null) ?: return false
        return Pin.check(pin, salt, hash)
    }

    fun clearPin() = sp.edit().remove("pin_salt").remove("pin_hash").apply()

    // ---- the quotas ----

    fun quota(key: String): Quota = Quota(
        enabled = sp.getBoolean("on.$key", false),
        weekdayMinutes = sp.getInt("week.$key", 30),
        weekendMinutes = sp.getInt("weekend.$key", 60)
    )

    fun setQuota(key: String, q: Quota) = sp.edit()
        .putBoolean("on.$key", q.enabled)
        .putInt("week.$key", q.weekdayMinutes.coerceIn(0, 1440))
        .putInt("weekend.$key", q.weekendMinutes.coerceIn(0, 1440))
        .apply()

    /** True when at least one section has a quota: the service has work. */
    fun anyEnabled(keys: Collection<String>): Boolean = keys.any { sp.getBoolean("on.$it", false) }

    /** The five-minute warning, for every section (default on). */
    var warnEnabled: Boolean
        get() = sp.getBoolean("warn", true)
        set(v) = sp.edit().putBoolean("warn", v).apply()

    // ---- the day's totals ----

    fun spent(dayKey: Int, key: String): Long = sp.getLong("spent.$dayKey.$key", 0L)

    /** Writes the day's total of one section and forgets every other day's figures. */
    fun setSpent(dayKey: Int, key: String, ms: Long) {
        val e = sp.edit().putLong("spent.$dayKey.$key", ms)
        prune(e, dayKey)
        e.apply()
    }

    fun totals(dayKey: Int): Map<String, Long> {
        val prefix = "spent.$dayKey."
        val m = HashMap<String, Long>()
        for ((k, v) in sp.all) if (k.startsWith(prefix) && v is Long) m[k.removePrefix(prefix)] = v
        return m
    }

    fun warned(dayKey: Int, key: String): Boolean = sp.getBoolean("warned.$dayKey.$key", false)
    fun setWarned(dayKey: Int, key: String) = sp.edit().putBoolean("warned.$dayKey.$key", true).apply()

    private fun prune(e: SharedPreferences.Editor, today: Int) {
        for (k in sp.all.keys) {
            if (!k.startsWith("spent.") && !k.startsWith("warned.")) continue
            val day = k.split('.').getOrNull(1)?.toIntOrNull() ?: continue
            if (day != today) e.remove(k)
        }
    }

    // ---- discovery mode: on for a while, then off by itself ----

    var discoveryUntil: Long
        get() = sp.getLong("discovery_until", 0L)
        set(v) = sp.edit().putLong("discovery_until", v).apply()

    fun discovering(now: Long = System.currentTimeMillis()): Boolean = now < discoveryUntil

    // ---- the service's state, as last seen ----

    /** When the accessibility service was last seen switched off while a quota was on; 0 = never. */
    var serviceOffSince: Long
        get() = sp.getLong("service_off_since", 0L)
        set(v) = sp.edit().putLong("service_off_since", v).apply()
}
