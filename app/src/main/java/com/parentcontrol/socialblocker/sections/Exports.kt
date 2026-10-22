package com.parentcontrol.socialblocker.sections

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * The exports of the family build (the rules in use, the discovery captures): written where the
 * parent chooses, a copy kept in the cache for the share menu that follows — the Reader's rule
 * for every export. Names say which app wrote them.
 */
object Exports {
    const val RULES_FILE = "readers-detox-section-rules.json"
    const val DISCOVERY_FILE = "readers-detox-discovery.txt"

    private fun dir(context: Context) = File(context.cacheDir, "exports")

    /** Writes the text to the chosen document and keeps the copy to share. False when the document could not be written. */
    fun save(context: Context, uri: Uri, name: String, text: String): Boolean {
        dir(context).listFiles()?.forEach { it.delete() }
        val written = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
        if (written) File(dir(context).apply { mkdirs() }, name).writeText(text)
        return written
    }

    /** Hands the copy of the last saved export to the share menu. */
    fun share(context: Context, name: String, mime: String, chooserTitle: String) {
        val file = File(dir(context), name)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
