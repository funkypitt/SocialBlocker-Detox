package com.parentcontrol.socialblocker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import com.parentcontrol.socialblocker.BlockPreferences
import com.parentcontrol.socialblocker.MainActivity
import com.parentcontrol.socialblocker.R
import com.parentcontrol.socialblocker.data.Hours
import java.util.Calendar

/**
 * The home-screen widget: the state in words on the left, a switch on the right.
 * Rendering only reads the preferences; every change of state calls [refresh].
 */
object DetoxWidget {
    private const val ACTION_TICK = "com.parentcontrol.socialblocker.widget.TICK"

    /** (background, foreground, dim) following the app's theme setting. */
    private fun colors(context: Context): Triple<Int, Int, Int> {
        val theme = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme", "DARK")
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (theme) { "LIGHT" -> false; "SYSTEM" -> systemDark; else -> true }
        return if (dark) Triple(Color.BLACK, Color.WHITE, Color.argb(140, 255, 255, 255))
        else Triple(Color.WHITE, Color.BLACK, Color.argb(140, 0, 0, 0))
    }

    private fun activity(context: Context, intent: Intent, code: Int): PendingIntent =
        PendingIntent.getActivity(context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun render(context: Context, mgr: AppWidgetManager, id: Int) {
        val b = BlockPreferences(context)
        val on = b.isBlockingEnabled
        val ranges = Hours.parse(b.schedule)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val v = RemoteViews(context.packageName, R.layout.widget_detox)
        val (bg, fg, dim) = colors(context)
        v.setInt(R.id.widget_root, "setBackgroundColor", bg)
        v.setTextColor(R.id.widget_title, fg)
        v.setTextColor(R.id.widget_sub, dim)
        v.setTextViewText(R.id.widget_title, context.getString(if (on) R.string.state_on else R.string.state_off))
        val open = Hours.openUntil(ranges, hour)
        v.setTextViewText(R.id.widget_sub, when {
            !on -> context.getString(R.string.detail_off)
            ranges.isEmpty() -> context.getString(R.string.w_all_day)
            open != null -> context.getString(R.string.w_open_until, open)
            else -> context.getString(R.string.w_open_at, Hours.nextOpen(ranges, hour) ?: 0)
        })
        // the switch: filled while blocking, a hairline frame when not
        v.setTextViewText(R.id.widget_switch, context.getString(if (on) R.string.w_on else R.string.w_off))
        if (on) v.setInt(R.id.widget_switch, "setBackgroundColor", fg)
        else v.setInt(R.id.widget_switch, "setBackgroundResource", if (fg == Color.WHITE) R.drawable.widget_frame_white else R.drawable.widget_frame_black)
        v.setTextColor(R.id.widget_switch, if (on) bg else fg)
        v.setOnClickPendingIntent(R.id.widget_body, activity(context, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN), 1))
        v.setOnClickPendingIntent(R.id.widget_switch, activity(context, Intent(context, WidgetActivity::class.java), 2))
        mgr.updateAppWidget(id, v)
    }

    /** Redraws every placed widget; keeps an hourly tick while the text depends on the hour. */
    fun refresh(context: Context) {
        val ctx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, DetoxWidgetProvider::class.java))
        ids.forEach { render(ctx, mgr, it) }
        val b = BlockPreferences(ctx)
        val tick = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, DetoxWidgetProvider::class.java).setAction(ACTION_TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = ctx.getSystemService(AlarmManager::class.java)
        if (ids.isNotEmpty() && b.isBlockingEnabled && b.schedule.isNotBlank()) {
            val next = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0) }
            alarms.set(AlarmManager.RTC, next.timeInMillis, tick)   // inexact is enough for a label
        } else alarms.cancel(tick)
    }

    fun isTick(intent: Intent) = intent.action == ACTION_TICK
}

class DetoxWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DetoxWidget.isTick(intent) || intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) DetoxWidget.refresh(context)
        else super.onReceive(context, intent)
    }
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = DetoxWidget.refresh(context)
    override fun onDisabled(context: Context) = DetoxWidget.refresh(context)
}
