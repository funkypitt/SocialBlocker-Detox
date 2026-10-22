package com.parentcontrol.socialblocker.sections

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Discovery mode, for the parent only: while it is on, each screen of a watched app is written
 * out as its tree of nodes — depth, class, view id, a short text, a short description, selected,
 * clickable — so that the markers of a section can be read off and put in the rules. Texts are
 * cut at 40 characters. Off, nothing of the screens is ever written anywhere.
 */
object Discovery {
    const val MAX_LINES = 220
    private const val MAX_FILES = 60
    private const val TEXT_MAX = 40
    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    private var lastBody = 0

    /** One node of the tree with its depth, as the service walked it. */
    class Line(val depth: Int, val node: Node)

    fun dir(context: Context): File = File(context.filesDir, "discovery").apply { mkdirs() }

    fun count(context: Context): Int = dir(context).listFiles()?.size ?: 0

    fun clear(context: Context) { dir(context).listFiles()?.forEach { it.delete() }; lastBody = 0 }

    /** Writes one dump unless it repeats the last one (content-changed events come in bursts). */
    fun dump(context: Context, pkg: String, activity: String, section: Section?, lines: List<Line>, now: Long) {
        val body = StringBuilder()
        var n = 0
        for (l in lines) {
            if (n++ >= MAX_LINES) { body.append("… ").append(lines.size - MAX_LINES).append(" more nodes\n"); break }
            body.append("  ".repeat(l.depth)).append(l.node.cls.substringAfterLast('.'))
            if (l.node.id.isNotEmpty()) body.append(" #").append(l.node.id.substringAfter(":id/", l.node.id))
            if (l.node.text.isNotEmpty()) body.append(" \"").append(short(l.node.text)).append('"')
            if (l.node.desc.isNotEmpty()) body.append(" (").append(short(l.node.desc)).append(')')
            if (l.node.selected) body.append(" [selected]")
            if (l.node.clickable) body.append(" [clickable]")
            body.append('\n')
        }
        val hash = body.toString().hashCode()
        if (hash == lastBody) return
        lastBody = hash
        val head = "time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))}\n" +
            "package: $pkg\nactivity: $activity\nsection: ${section?.key ?: "-"}\nnodes: ${lines.size}\n\n"
        val d = dir(context)
        runCatching { File(d, "$pkg-${stamp.format(Date(now))}.txt").writeText(head + body) }
        val files = d.listFiles()?.sortedBy { it.name } ?: return
        if (files.size > MAX_FILES) files.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    /** Every dump in one text, oldest first, for the export. */
    fun all(context: Context): String {
        val files = dir(context).listFiles()?.sortedBy { it.name } ?: return ""
        return files.joinToString("\n\n========================================\n\n") { "## ${it.name}\n\n" + it.readText() }
    }

    private fun short(s: String): String {
        val t = s.replace('\n', ' ').trim()
        return if (t.length <= TEXT_MAX) t else t.take(TEXT_MAX - 1) + "…"
    }
}
