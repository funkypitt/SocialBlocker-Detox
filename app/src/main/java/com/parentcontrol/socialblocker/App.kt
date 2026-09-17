package com.parentcontrol.socialblocker

import android.app.Application
import com.parentcontrol.socialblocker.data.Prefs

class App : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    val block: BlockPreferences by lazy { BlockPreferences(this) }
    override fun onCreate() { super.onCreate(); prefs }
}
