package com.parentcontrol.socialblocker.sections

import org.json.JSONArray
import org.json.JSONObject

/**
 * The detection rules: which screens of which apps count as a section with a quota. They come
 * from a JSON file (the bundled one, or the one the parent imported), never from the code, so a
 * WhatsApp or CapCut update that renames its views is answered with a new file, not a build.
 * Format in docs/RULES.md. Pure Kotlin on org.json, tested from the JVM.
 */

/** One screen the rules refer to: the package, the activity in front, the nodes on the screen. */
data class Node(
    val id: String = "",
    val cls: String = "",
    val text: String = "",
    val desc: String = "",
    val selected: Boolean = false,
    val clickable: Boolean = false
)

data class Snapshot(val pkg: String, val activity: String, val nodes: List<Node>)

/**
 * One selector. The keys given are all required of the same node (AND); [activity] is about
 * the window and never about a node. Patterns are exact matches, `*` standing for any run of
 * characters, case ignored. The texts and descriptions are per language and any language's
 * entry may match: the phone's language is not known to the rules.
 */
data class Selector(
    val id: String? = null,
    val cls: String? = null,
    val texts: List<String> = emptyList(),
    val descs: List<String> = emptyList(),
    val selected: Boolean? = null,
    val clickable: Boolean? = null,
    val activity: String? = null
) {
    val aboutNodes: Boolean get() = id != null || cls != null || texts.isNotEmpty() || descs.isNotEmpty() || selected != null || clickable != null

    fun matchesActivity(activity: String): Boolean = this.activity == null || Rules.glob(this.activity, activity)

    fun matchesNode(n: Node): Boolean {
        if (id != null && !Rules.glob(id, n.id)) return false
        if (cls != null && !Rules.glob(cls, n.cls)) return false
        if (texts.isNotEmpty() && texts.none { Rules.glob(it, n.text) }) return false
        if (descs.isNotEmpty() && descs.none { Rules.glob(it, n.desc) }) return false
        if (selected != null && selected != n.selected) return false
        if (clickable != null && clickable != n.clickable) return false
        return true
    }

    /** True when the selector holds on this screen: the activity, and one node when it names nodes. */
    fun matches(s: Snapshot): Boolean =
        matchesActivity(s.activity) && (!aboutNodes || s.nodes.any { matchesNode(it) })

    /** The first node the selector names on this screen, or -1. */
    fun firstNode(s: Snapshot): Int {
        if (!matchesActivity(s.activity) || !aboutNodes) return -1
        return s.nodes.indexOfFirst { matchesNode(it) }
    }
}

/** A section of an app: in front when one of [any] holds and none of [none] does. */
data class Section(
    val pkg: String,
    val id: String,
    val names: Map<String, String>,
    val any: List<Selector>,
    val none: List<Selector>,
    /** Nodes to tap to leave the section (the Chats tab, the editor tab), tried in order. */
    val escape: List<Selector>
) {
    val key: String get() = "$pkg/$id"

    fun name(language: String): String = names[language] ?: names["en"] ?: names.values.firstOrNull() ?: id

    fun inFront(s: Snapshot): Boolean =
        s.pkg == pkg && any.any { it.matches(s) } && none.none { it.matches(s) }
}

data class AppRules(val pkg: String, val name: String, val sections: List<Section>)

class Rules(val version: Int, val apps: List<AppRules>) {
    val packages: Set<String> get() = apps.map { it.pkg }.toSet()
    val sections: List<Section> get() = apps.flatMap { it.sections }

    /** The section in front on this screen, the first that holds in file order; null outside every section. */
    fun sectionOf(s: Snapshot): Section? = apps.firstOrNull { it.pkg == s.pkg }?.sections?.firstOrNull { it.inFront(s) }

    fun section(key: String): Section? = sections.firstOrNull { it.key == key }

    companion object {
        val EMPTY = Rules(0, emptyList())

        /** Parses the file; keys starting with `_` are comments and ignored. Throws on a malformed file. */
        fun parse(json: String): Rules {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            val pk = root.optJSONObject("packages") ?: JSONObject()
            val apps = ArrayList<AppRules>()
            for (pkg in pk.keys()) {
                if (pkg.startsWith("_")) continue
                val a = pk.getJSONObject(pkg)
                val secs = a.optJSONObject("sections") ?: JSONObject()
                val sections = ArrayList<Section>()
                for (sid in secs.keys()) {
                    if (sid.startsWith("_")) continue
                    val o = secs.getJSONObject(sid)
                    sections += Section(
                        pkg, sid,
                        names = strings(o.optJSONObject("name")),
                        any = selectors(o.optJSONArray("any")),
                        none = selectors(o.optJSONArray("none")),
                        escape = selectors(o.optJSONArray("escape"))
                    )
                }
                apps += AppRules(pkg, a.optString("name", pkg), sections)
            }
            return Rules(version, apps)
        }

        private fun strings(o: JSONObject?): Map<String, String> {
            if (o == null) return emptyMap()
            val m = LinkedHashMap<String, String>()
            for (k in o.keys()) if (!k.startsWith("_")) m[k] = o.getString(k)
            return m
        }

        private fun selectors(a: JSONArray?): List<Selector> {
            if (a == null) return emptyList()
            return (0 until a.length()).map { selector(a.getJSONObject(it)) }
        }

        private fun selector(o: JSONObject): Selector = Selector(
            id = o.optString("id", "").ifEmpty { null },
            cls = o.optString("class", "").ifEmpty { null },
            texts = perLanguage(o.opt("text")),
            descs = perLanguage(o.opt("desc")),
            selected = if (o.has("selected")) o.getBoolean("selected") else null,
            clickable = if (o.has("clickable")) o.getBoolean("clickable") else null,
            activity = o.optString("activity", "").ifEmpty { null }
        )

        /** `"text": "Updates"`, `["Updates", "Actus"]` or `{"en": ["Updates"], "fr": "Actus"}`: all the values, flat. */
        private fun perLanguage(v: Any?): List<String> = when (v) {
            null -> emptyList()
            is String -> listOf(v)
            is JSONArray -> (0 until v.length()).map { v.getString(it) }
            is JSONObject -> v.keys().asSequence().filter { !it.startsWith("_") }.flatMap { perLanguage(v.get(it)).asSequence() }.toList()
            else -> emptyList()
        }

        /** [pattern] against [value], `*` any run, case ignored. */
        fun glob(pattern: String, value: String): Boolean {
            val p = pattern.lowercase(); val v = value.lowercase()
            var pi = 0; var vi = 0; var star = -1; var mark = 0
            while (vi < v.length) {
                when {
                    pi < p.length && p[pi] == '*' -> { star = pi++; mark = vi }
                    pi < p.length && p[pi] == v[vi] -> { pi++; vi++ }
                    star >= 0 -> { pi = star + 1; vi = ++mark }
                    else -> return false
                }
            }
            while (pi < p.length && p[pi] == '*') pi++
            return pi == p.length
        }
    }
}
