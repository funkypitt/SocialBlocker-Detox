package com.parentcontrol.socialblocker

import android.content.Context
import android.content.Intent
import android.os.Build
import com.parentcontrol.socialblocker.widget.DetoxWidget

/** Starts and stops the DNS VPN. The app's button and the widget both go through here. */
object Blocker {
    fun start(context: Context, prefs: BlockPreferences) {
        prefs.setBlockingEnabled(true)
        val intent = Intent(context, SocialBlockerVpnService::class.java).setAction("START")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        DetoxWidget.refresh(context)
    }

    fun stop(context: Context, prefs: BlockPreferences) {
        prefs.setBlockingEnabled(false)
        context.startService(Intent(context, SocialBlockerVpnService::class.java).setAction("STOP"))
        DetoxWidget.refresh(context)
    }

    /**
     * The only way to stop on request: straight away when the maths gate is off, otherwise
     * [openChallenge] — the challenge screen stops blocking once the five problems are solved.
     */
    fun stopOrChallenge(context: Context, prefs: BlockPreferences, openChallenge: () -> Unit) {
        if (prefs.isRequireMathToUnblock) openChallenge() else stop(context, prefs)
    }
}
