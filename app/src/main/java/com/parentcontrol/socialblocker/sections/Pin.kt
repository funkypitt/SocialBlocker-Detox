package com.parentcontrol.socialblocker.sections

import java.security.MessageDigest
import java.security.SecureRandom

/** The parent's PIN, kept as a salted SHA-256 digest: the digits themselves are never stored. */
object Pin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun valid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return hex(b)
    }

    fun hash(pin: String, salt: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8)))

    fun check(pin: String, salt: String, expected: String): Boolean {
        val h = hash(pin, salt)
        // constant time, for the form of it
        if (h.length != expected.length) return false
        var diff = 0
        for (i in h.indices) diff = diff or (h[i].code xor expected[i].code)
        return diff == 0
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
