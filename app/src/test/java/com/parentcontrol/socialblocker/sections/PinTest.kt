package com.parentcontrol.socialblocker.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {
    @Test
    fun validity() {
        assertTrue(Pin.valid("1234")); assertTrue(Pin.valid("12345678"))
        assertFalse(Pin.valid("123")); assertFalse(Pin.valid("123456789")); assertFalse(Pin.valid("12a4")); assertFalse(Pin.valid(""))
    }

    @Test
    fun hashRoundTrip() {
        val salt = Pin.newSalt()
        val h = Pin.hash("4321", salt)
        assertEquals(64, h.length)
        assertTrue(Pin.check("4321", salt, h))
        assertFalse(Pin.check("4322", salt, h))
        assertFalse(Pin.check("4321", Pin.newSalt(), h))
        assertNotEquals(h, Pin.hash("4321", Pin.newSalt()))
        assertFalse(Pin.check("4321", salt, ""))
    }
}
