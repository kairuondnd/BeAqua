package com.example.beaqua

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHelper {
    private const val PREFIX = "pbkdf2_sha256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(password: String): String {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val derivedKey = derive(password, salt, ITERATIONS)
        return "$PREFIX\$$ITERATIONS\$${salt.toHex()}\$${derivedKey.toHex()}"
    }

    fun verify(password: String, storedValue: String): Boolean {
        val parts = storedValue.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) {
            return password == storedValue
        }

        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2].hexToBytesOrNull() ?: return false
        val expected = parts[3].hexToBytesOrNull() ?: return false
        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    fun isHashed(storedValue: String): Boolean = storedValue.startsWith("$PREFIX\$")

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val specification = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .encoded
        } finally {
            specification.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { index ->
                substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
