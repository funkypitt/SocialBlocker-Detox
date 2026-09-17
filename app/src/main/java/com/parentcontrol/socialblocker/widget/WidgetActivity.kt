package com.parentcontrol.socialblocker.widget

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import com.parentcontrol.socialblocker.BlockPreferences
import com.parentcontrol.socialblocker.Blocker
import com.parentcontrol.socialblocker.MainActivity

/**
 * The widget's switch. Invisible: it decides at the moment of the tap, from the current
 * state, then leaves. Off → start (or the app, for the VPN consent). On → the same stop
 * as the app's button: straight away, or the maths challenge when the gate is on.
 */
class WidgetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = BlockPreferences(this)
        if (!b.isBlockingEnabled) {
            if (VpnService.prepare(this) == null) Blocker.start(this, b)
            else openApp(MainActivity.ACTION_START)
        } else {
            Blocker.stopOrChallenge(this, b) { openApp(MainActivity.ACTION_CHALLENGE) }
        }
        finish()
    }

    private fun openApp(action: String) {
        startActivity(Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
}
