package com.parentcontrol.socialblocker.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RulesTest {
    private val json = """
    {
      "version": 3,
      "packages": {
        "_comment": "ignored",
        "com.whatsapp": {
          "name": "WhatsApp",
          "sections": {
            "_note": "ignored too",
            "updates": {
              "name": {"en": "Updates", "fr": "Actus"},
              "any": [
                {"desc": {"en": ["Updates"], "fr": "Actus", "pl": ["Aktualizacje"]}, "selected": true},
                {"id": "com.whatsapp:id/*newsletter*"},
                {"activity": "*NewsletterActivity"}
              ],
              "none": [ {"id": "com.whatsapp:id/compose" } ],
              "escape": [ {"desc": ["Chats", "Discussions"], "clickable": true}, {"id": "*chats_tab"} ]
            }
          }
        }
      }
    }"""
    private val rules = Rules.parse(json)
    private val updates = rules.section("com.whatsapp/updates")!!

    private fun home(vararg nodes: Node) = Snapshot("com.whatsapp", "com.whatsapp.HomeActivity", nodes.toList())
    private val tabChats = Node(id = "com.whatsapp:id/chats_tab", desc = "Discussions", selected = true, clickable = true)
    private val tabUpdates = Node(id = "com.whatsapp:id/updates_tab", desc = "Actus", selected = false, clickable = true)

    @Test
    fun parsesTheFile() {
        assertEquals(3, rules.version)
        assertEquals(setOf("com.whatsapp"), rules.packages)
        assertEquals("Actus", updates.name("fr"))
        assertEquals("Updates", updates.name("de"))   // falls back on English
        assertEquals(3, updates.any.size)
        assertEquals(listOf("Updates", "Actus", "Aktualizacje"), updates.any[0].descs)
        assertEquals(true, updates.any[0].selected)
        assertEquals(2, updates.escape.size)
    }

    @Test
    fun chatsTabInFrontIsNoSection() {
        assertNull(rules.sectionOf(home(tabChats, tabUpdates)))
    }

    @Test
    fun selectedUpdatesTabIsTheSection() {
        val s = home(tabChats.copy(selected = false), tabUpdates.copy(selected = true))
        assertEquals(updates, rules.sectionOf(s))
    }

    @Test
    fun idGlobAndActivityGlobMatch() {
        assertNotNull(rules.sectionOf(home(Node(id = "com.whatsapp:id/newsletter_header"))))
        assertNotNull(rules.sectionOf(Snapshot("com.whatsapp", "com.whatsapp.newsletter.ui.NewsletterActivity", emptyList())))
        assertNull(rules.sectionOf(Snapshot("com.whatsapp", "com.whatsapp.Conversation", emptyList())))
    }

    @Test
    fun anotherPackageNeverMatches() {
        assertNull(rules.sectionOf(Snapshot("com.whatsapp.w4b", "x.NewsletterActivity", listOf(tabUpdates.copy(selected = true)))))
    }

    @Test
    fun aNoneSelectorExcludes() {
        val s = home(tabUpdates.copy(selected = true), Node(id = "com.whatsapp:id/compose"))
        assertNull(rules.sectionOf(s))
    }

    @Test
    fun textsMatchCaseInsensitively() {
        assertNotNull(rules.sectionOf(home(Node(desc = "updates", selected = true))))
        assertNull(rules.sectionOf(home(Node(desc = "updates today", selected = true))))
    }

    @Test
    fun escapeFindsTheNodeToTap() {
        val s = home(tabUpdates.copy(selected = true), tabChats.copy(selected = false))
        assertEquals(1, updates.escape[0].firstNode(s))
        assertEquals(-1, updates.escape[0].firstNode(home(tabUpdates.copy(selected = true))))
        assertEquals(1, updates.escape[1].firstNode(s))
    }

    @Test
    fun glob() {
        assertTrue(Rules.glob("*", "anything"))
        assertTrue(Rules.glob("*", ""))
        assertTrue(Rules.glob("a*c", "abc"))
        assertTrue(Rules.glob("a*c", "ac"))
        assertTrue(Rules.glob("*:id/*tab*", "com.whatsapp:id/updates_tab_x"))
        assertFalse(Rules.glob("a*c", "abd"))
        assertFalse(Rules.glob("abc", "abcd"))
        assertTrue(Rules.glob("Actus", "actus"))
    }

    @Test
    fun theBundledRulesParseAndNameBothApps() {
        val bundled = Rules.parse(File("src/main/assets/section_rules.json").readText())
        assertEquals(setOf("com.whatsapp", "com.lemon.lvoverseas"), bundled.packages)
        val w = bundled.section("com.whatsapp/updates")!!
        val c = bundled.section("com.lemon.lvoverseas/templates")!!
        assertTrue(w.escape.isNotEmpty() && c.escape.isNotEmpty())
        // the labels, selected, are the markers to trust first
        assertEquals(w, bundled.sectionOf(Snapshot("com.whatsapp", "com.whatsapp.HomeActivity", listOf(Node(desc = "Actus", selected = true)))))
        assertEquals(c, bundled.sectionOf(Snapshot("com.lemon.lvoverseas", "x.MainActivity", listOf(Node(text = "Modèles", selected = true)))))
        assertNull(bundled.sectionOf(Snapshot("com.whatsapp", "com.whatsapp.HomeActivity", listOf(Node(desc = "Discussions", selected = true), Node(desc = "Actus")))))
        // the six languages of the app have a name
        for (l in listOf("en", "fr", "de", "es", "pt", "ru")) { assertTrue(w.names.containsKey(l)); assertTrue(c.names.containsKey(l)) }
    }

    @Test
    fun emptyAndMalformed() {
        assertEquals(0, Rules.parse("{}").apps.size)
        try { Rules.parse("not json"); assertTrue(false) } catch (e: org.json.JSONException) { }
    }
}
