package com.parentcontrol.socialblocker.sections

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Where the rules come from: the file the parent imported (`files/section_rules.json`) when
 * there is one and it parses, otherwise the one bundled in the assets. Loaded once, re-read
 * after an import or a reset; the service hears of the change through [onChange].
 */
object RulesRepo {
    private const val TAG = "DetoxSections"
    private const val FILE = "section_rules.json"

    enum class Source { BUNDLED, CUSTOM }

    @Volatile private var cached: Rules? = null
    @Volatile var source: Source = Source.BUNDLED
        private set
    /** Set by the service while it runs. */
    @Volatile var onChange: (() -> Unit)? = null

    private fun customFile(context: Context) = File(context.filesDir, FILE)

    fun rules(context: Context): Rules {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val f = customFile(context)
            if (f.isFile) {
                runCatching { Rules.parse(f.readText()) }
                    .onSuccess { cached = it; source = Source.CUSTOM; return it }
                    .onFailure { Log.w(TAG, "custom rules unreadable, back to the bundled ones: $it") }
            }
            val r = runCatching { Rules.parse(context.assets.open(FILE).bufferedReader().readText()) }
                .getOrElse { Log.e(TAG, "bundled rules: $it"); Rules.EMPTY }
            cached = r; source = Source.BUNDLED
            return r
        }
    }

    /** The file is parsed first: a broken one is refused and the rules in use stay. */
    fun import(context: Context, uri: Uri): Result<Rules> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
        val r = Rules.parse(text)
        require(r.apps.isNotEmpty()) { "no package in the file" }
        customFile(context).writeText(text)
        reload(context)
        r
    }

    fun reset(context: Context) {
        customFile(context).delete()
        reload(context)
    }

    /** The text of the rules in use, for the export. */
    fun text(context: Context): String {
        val f = customFile(context)
        return if (f.isFile) f.readText() else context.assets.open(FILE).bufferedReader().readText()
    }

    private fun reload(context: Context) {
        synchronized(this) { cached = null }
        rules(context)
        onChange?.invoke()
    }
}
