package com.parentcontrol.socialblocker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.parentcontrol.socialblocker.ui.SITES
import com.parentcontrol.socialblocker.widget.DetoxWidget
import java.util.Calendar

/**
 * Counts the day's minutes in the apps of the chosen sites, from the phone's usage events, and
 * writes the count in the preferences where the DNS decision reads it. While the VPN runs it
 * measures again when the minutes left could be spent (at most every two minutes while the
 * screen is on, not at all while it is off), at screen on and unlock, and at midnight; the app
 * measures when it opens and the boot receiver after a start.
 */
object BudgetMonitor {
    private const val TAG = "DetoxBudget"
    private const val MIN_DELAY = 15_000L
    private const val MAX_DELAY = 120_000L
    /** Events are read from this long before midnight: a stretch open at midnight started before it. */
    private const val LOOK_BACK = 12 * 3600_000L

    private val handler = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var receiver: BroadcastReceiver? = null
    private val tick = Runnable { schedule() }

    /** True once "usage access" is given to the app in the system settings. */
    fun allowed(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT)
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        else mode == AppOpsManager.MODE_ALLOWED
    }

    /** The known packages of the chosen sites. */
    fun counted(prefs: BlockPreferences): Set<String> = SitePackages.packages(SITES.filter { it.get(prefs) }.map { it.name })

    /** Of the known packages of one site, those installed on this phone. */
    fun installed(context: Context, site: String): List<String> = SitePackages.of(site).filter {
        runCatching { context.packageManager.getApplicationInfo(it, 0) }.isSuccess
    }

    /** The app's name as the phone shows it. */
    fun label(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    /**
     * Measures today's minutes, stores them and redraws the widget. Returns the milliseconds
     * spent, 0 without a budget or without usage access.
     */
    fun measure(context: Context): Long {
        val prefs = BlockPreferences(context)
        if (prefs.budgetMinutes <= 0 || !allowed(context)) return 0L
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val dayStart = Budget.dayStart(cal)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = ArrayList<Budget.Event>()
        runCatching {
            val q = usm.queryEvents(dayStart - LOOK_BACK, now)
            val e = UsageEvents.Event()
            while (q.hasNextEvent()) {
                q.getNextEvent(e)
                val kind = when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> Budget.RESUMED
                    UsageEvents.Event.ACTIVITY_PAUSED -> Budget.PAUSED
                    UsageEvents.Event.ACTIVITY_STOPPED -> Budget.STOPPED
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> Budget.SCREEN_OFF
                    UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> Budget.BOOT
                    else -> 0
                }
                if (kind != 0) events += Budget.Event(e.timeStamp, kind, e.packageName, e.className)
            }
        }.onFailure { Log.w(TAG, "usage events: $it") }
        val spent = Budget.spent(events, counted(prefs), dayStart, now)
        prefs.setBudgetSpent(Budget.dayKey(cal), spent)
        DetoxWidget.refresh(context)
        return spent
    }

    /** The VPN service calls this once it runs: measure now and keep measuring while it runs. */
    fun start(service: Context) {
        val c = service.applicationContext
        if (ctx != null) return
        ctx = c
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { schedule() }
        }
        receiver = r
        runCatching {
            c.registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF)
            })
        }
        schedule()
    }

    fun stop() {
        handler.removeCallbacks(tick)
        val c = ctx ?: return
        receiver?.let { runCatching { c.unregisterReceiver(it) } }
        receiver = null
        ctx = null
    }

    /** Measure, then decide when to measure again. */
    private fun schedule() {
        val c = ctx ?: return
        handler.removeCallbacks(tick)
        val spent = measure(c)
        val prefs = BlockPreferences(c)
        val now = System.currentTimeMillis()
        val midnight = Budget.nextMidnight(Calendar.getInstance()) - now + 1_000L
        val delay = if (prefs.budgetMinutes <= 0 || !allowed(c)) midnight
        else {
            val left = prefs.budgetMinutes * 60_000L - spent
            val screenOn = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            if (left <= 0 || !screenOn) midnight else minOf(midnight, left.coerceIn(MIN_DELAY, MAX_DELAY))
        }
        handler.postDelayed(tick, delay)
    }
}
