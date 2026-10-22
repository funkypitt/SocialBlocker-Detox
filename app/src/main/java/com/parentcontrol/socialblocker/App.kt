package com.parentcontrol.socialblocker

import android.app.Application
import com.parentcontrol.socialblocker.data.Prefs
import com.parentcontrol.socialblocker.sections.SectionPrefs

class App : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    val block: BlockPreferences by lazy { BlockPreferences(this) }
    /** The family build's PIN and section quotas (read by the public build too, where the PIN is never set). */
    val sections: SectionPrefs by lazy { SectionPrefs(this) }
    override fun onCreate() { super.onCreate(); prefs }
}
