package com.parentcontrol.socialblocker

import android.content.Context
import android.content.Intent
import android.os.Build

/** Starts and stops the DNS VPN, the way the old MainActivity did. */
object Blocker {
    fun start(context: Context, prefs: BlockPreferences) {
        prefs.setBlockingEnabled(true)
        val intent = Intent(context, SocialBlockerVpnService::class.java).setAction("START")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    fun stop(context: Context, prefs: BlockPreferences) {
        prefs.setBlockingEnabled(false)
        context.startService(Intent(context, SocialBlockerVpnService::class.java).setAction("STOP"))
    }
}
