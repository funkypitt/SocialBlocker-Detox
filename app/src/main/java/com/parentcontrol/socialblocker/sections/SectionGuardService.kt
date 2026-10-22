package com.parentcontrol.socialblocker.sections

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.parentcontrol.socialblocker.MainActivity
import com.parentcontrol.socialblocker.R

/**
 * The guard of the sections (family build). An accessibility service limited to the packages
 * the rules name. On each window change, selection or (throttled) content change it takes a
 * structural snapshot of the screen — view ids, classes, texts, descriptions, selected,
 * clickable — asks the rules which section is in front, and feeds the stopwatch. While a
 * section runs it also looks once a second on its own, because a restricted service hears
 * nothing when the child moves to another app, and because a quota can run out on a still
 * screen. The quota reached, it taps the rules' escape node (the Chats tab, the editor), or
 * goes back, and says so in a toast; an action guard keeps that from looping.
 *
 * Nothing of a screen is kept or logged outside discovery mode; the snapshot lives for the
 * length of one evaluation.
 */
class SectionGuardService : AccessibilityService() {
    companion object {
        private const val TAG = "DetoxSections"
        private const val CONTENT_THROTTLE_MS = 400L
        private const val TICK_MS = 1_000L
        private const val SAVE_EVERY_MS = 30_000L
        private const val TOAST_EVERY_MS = 8_000L
        private const val MAX_NODES = 400
        private const val MAX_DEPTH = 30
        private const val CHANNEL = "sections"
        private const val NOTIFICATION_OFF = 2

        /**
         * True when the service is switched on in the accessibility settings: the system's own
         * list of enabled services first (the raw setting is not always what it stores), then
         * the raw setting, and the service's word when it runs in this very process.
         */
        fun isEnabled(context: Context): Boolean {
            if (running) return true
            val me = ComponentName(context, SectionGuardService::class.java)
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabled = runCatching { am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) }.getOrNull()
            if (enabled != null && enabled.any { it.id == me.flattenToString() || it.resolveInfo?.serviceInfo?.let { s -> s.packageName == me.packageName && s.name == me.className } == true }) return true
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true) }
        }

        fun openSettings(context: Context) {
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        /** The service tells the app when it is up, so the parent's screen can say "running". */
        @Volatile var running: Boolean = false
            private set

        /**
         * Force-stopping the app switches its accessibility service off without a word to it
         * (no onUnbind): the app looks for itself when it opens, after a boot and from the
         * budget's clock, and tells the parent once per such stop.
         */
        fun checkOff(context: Context) {
            if (!com.parentcontrol.socialblocker.BuildConfig.FAMILLE) return
            val prefs = SectionPrefs(context)
            val rules = RulesRepo.rules(context)
            if (isEnabled(context) || !prefs.anyEnabled(rules.sections.map { it.key })) return
            if (prefs.serviceOffSince != 0L) return
            prefs.serviceOffSince = System.currentTimeMillis()
            notifyOff(context)
        }

        fun notifyOff(context: Context) {
            val mgr = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mgr.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.sec_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
            b.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.sec_off_title))
                .setContentText(context.getString(R.string.sec_off_text))
                .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.sec_off_text)))
                .setContentIntent(open)
                .setAutoCancel(true)
            runCatching { mgr.notify(NOTIFICATION_OFF, b.build()) }
        }
    }

    private lateinit var prefs: SectionPrefs
    private var rules: Rules = Rules.EMPTY
    private val clock = LocalDayClock()
    private lateinit var timer: SectionTimer
    private val guard = ActionGuard()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingEval = false
    private val evalNow = Runnable { pendingEval = false; evaluate() }
    private val tick = object : Runnable { override fun run() { evaluate(); if (timer.active) handler.postDelayed(this, TICK_MS) } }
    private var ticking = false
    private var lastSave = 0L
    private var lastToast = 0L
    /** The last activity-looking class name each watched package put in front. */
    private val activityOf = HashMap<String, String>()
    private var receiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = SectionPrefs(this)
        timer = SectionTimer(clock) { day, key, total -> prefs.setSpent(day, key, total) }
        val now = System.currentTimeMillis()
        timer.seed(clock.dayKey(now), prefs.totals(clock.dayKey(now)))
        rules = RulesRepo.rules(this)
        applyInfo()
        RulesRepo.onChange = { handler.post { rules = RulesRepo.rules(this); applyInfo(); Log.i(TAG, "rules reloaded: v${rules.version} ${rules.packages}") } }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> { timer.screenOff(System.currentTimeMillis()); stopTicking() }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> handler.postDelayed(evalNow, 500)
                }
            }
        }
        receiver = r
        runCatching { registerReceiver(r, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }) }
        prefs.serviceOffSince = 0L
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_OFF)
        running = true
        Log.i(TAG, "guard connected, rules v${rules.version}, packages ${rules.packages}")
    }

    /** The event filter, from the rules: only the packages they name, the three event types, view ids. */
    private fun applyInfo() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SELECTED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 300
        // no package would mean every package: with no rules, listen to ourselves only
        info.packageNames = rules.packages.ifEmpty { setOf(packageName) }.toTypedArray()
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in rules.packages) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!pendingEval) { pendingEval = true; handler.postDelayed(evalNow, CONTENT_THROTTLE_MS) }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val cls = event.className?.toString() ?: ""
                // dialogs and popups report widget classes; an activity's name is the app's own
                if (cls.isNotEmpty() && !cls.startsWith("android.") && !cls.startsWith("androidx.")) activityOf[pkg] = cls
                handler.removeCallbacks(evalNow); pendingEval = false
                evaluate()
            }
            else -> { handler.removeCallbacks(evalNow); pendingEval = false; evaluate() }
        }
    }

    override fun onInterrupt() {}

    /** One look: what is in front, the stopwatch, the save, the quota. */
    private fun evaluate() {
        val now = System.currentTimeMillis()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) { timer.screenOff(now); stopTicking(); return }
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val pkg = root?.packageName?.toString() ?: ""
        var section: Section? = null
        var snap: Snapshot? = null
        var refs: List<AccessibilityNodeInfo> = emptyList()
        if (root != null && pkg in rules.packages) {
            val lines = ArrayList<Discovery.Line>()
            val nodes = ArrayList<AccessibilityNodeInfo>()
            walk(root, 0, lines, nodes)
            snap = Snapshot(pkg, activityOf[pkg] ?: "", lines.map { it.node })
            refs = nodes
            section = rules.sectionOf(snap)
            if (prefs.discovering(now)) Discovery.dump(this, pkg, snap.activity, section, lines, now)
        }
        timer.observe(section?.key, now)
        if (timer.active) startTicking() else stopTicking()
        timer.current?.let { cur ->
            if (now - lastSave >= SAVE_EVERY_MS) { prefs.setSpent(clock.dayKey(now), cur, timer.spentToday(cur, now)); lastSave = now }
        }
        if (section != null && snap != null) enforce(section, snap, refs, now)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { refs.forEach { runCatching { @Suppress("DEPRECATION") it.recycle() } }; runCatching { @Suppress("DEPRECATION") root?.recycle() } }
    }

    /** The quota of the section in front: leave it when spent, warn five minutes before. */
    private fun enforce(section: Section, snap: Snapshot, refs: List<AccessibilityNodeInfo>, now: Long) {
        val q = prefs.quota(section.key)
        if (!q.enabled) return
        val weekend = clock.isWeekend(now)
        val spent = timer.spentToday(section.key, now)
        val name = section.name(resources.configuration.locales[0].language)
        if (q.exhausted(spent, weekend)) {
            if (guard.allow(now)) {
                toast(getString(R.string.sec_quota_reached, name), now, force = true)
                if (!clickEscape(section, snap, refs)) performGlobalAction(GLOBAL_ACTION_BACK)
                Log.i(TAG, "quota reached for ${section.key}: left")
            } else toast(getString(R.string.sec_quota_reached, name), now, force = false)
            return
        }
        val day = clock.dayKey(now)
        if (prefs.warnEnabled && q.warnDue(spent, weekend) && !prefs.warned(day, section.key)) {
            prefs.setWarned(day, section.key)
            toast(resources.getQuantityString(R.plurals.sec_minutes_left, q.minutesLeft(spent, weekend), name, q.minutesLeft(spent, weekend)), now, force = true)
        }
    }

    /** Taps the first escape node the rules find on this screen (or its nearest clickable parent). */
    private fun clickEscape(section: Section, snap: Snapshot, refs: List<AccessibilityNodeInfo>): Boolean {
        for (sel in section.escape) {
            val i = sel.firstNode(snap)
            if (i < 0 || i >= refs.size) continue
            var n: AccessibilityNodeInfo? = refs[i]
            var up = 0
            while (n != null && !n.isClickable && up++ < 5) n = n.parent
            if (n != null && n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun toast(text: String, now: Long, force: Boolean) {
        if (!force && now - lastToast < TOAST_EVERY_MS) return
        lastToast = now
        runCatching { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }

    /** Depth-first, bounded: the structure of the screen, never more than [MAX_NODES] nodes. */
    private fun walk(n: AccessibilityNodeInfo, depth: Int, lines: MutableList<Discovery.Line>, refs: MutableList<AccessibilityNodeInfo>) {
        if (lines.size >= MAX_NODES || depth > MAX_DEPTH) return
        lines += Discovery.Line(depth, Node(
            id = n.viewIdResourceName ?: "",
            cls = n.className?.toString() ?: "",
            text = n.text?.toString()?.take(80) ?: "",
            desc = n.contentDescription?.toString()?.take(80) ?: "",
            selected = n.isSelected,
            clickable = n.isClickable
        ))
        refs += n
        for (i in 0 until n.childCount) {
            val c = runCatching { n.getChild(i) }.getOrNull() ?: continue
            walk(c, depth + 1, lines, refs)
        }
    }

    private fun startTicking() { if (!ticking) { ticking = true; handler.postDelayed(tick, TICK_MS) } }
    private fun stopTicking() { ticking = false; handler.removeCallbacks(tick) }

    override fun onUnbind(intent: Intent?): Boolean {
        // switched off in the settings, or the system let go of us: whatever runs is saved,
        // and the parent is told when a quota was counting on the service
        val now = System.currentTimeMillis()
        runCatching { timer.screenOff(now) }
        stopTicking(); handler.removeCallbacks(evalNow)
        receiver?.let { runCatching { unregisterReceiver(it) } }; receiver = null
        RulesRepo.onChange = null
        running = false
        if (::prefs.isInitialized && prefs.anyEnabled(rules.sections.map { it.key })) {
            prefs.serviceOffSince = now
            notifyOff(this)
        }
        Log.i(TAG, "guard unbound")
        return super.onUnbind(intent)
    }
}
