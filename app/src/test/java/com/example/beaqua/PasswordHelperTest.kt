package com.example.beaqua

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHelperTest {

    @Test
    fun hashedPasswordCanBeVerified() {
        val stored = PasswordHelper.hash("station-password")

        assertTrue(PasswordHelper.isHashed(stored))
        assertTrue(PasswordHelper.verify("station-password", stored))
        assertFalse(PasswordHelper.verify("wrong-password", stored))
    }

    @Test
    fun equalPasswordsReceiveDifferentSalts() {
        val first = PasswordHelper.hash("same-password")
        val second = PasswordHelper.hash("same-password")

        assertNotEquals(first, second)
        assertTrue(PasswordHelper.verify("same-password", first))
        assertTrue(PasswordHelper.verify("same-password", second))
    }

    @Test
    fun legacyPlaintextValueCanBeVerifiedForMigration() {
        assertTrue(PasswordHelper.verify("legacy-password", "legacy-password"))
        assertFalse(PasswordHelper.verify("wrong-password", "legacy-password"))
    }
}
