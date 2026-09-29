package com.example.beaqua

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local opt-in session. Neither the password nor a reusable password hash is saved. */
object RememberedSession {
    private const val KEY_ALIAS = "beaqua.remembered_session.v1"
    data class Account(val username: String, val role: String, val credentialFingerprint: String)

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "remembered_session"))
    fun exists(context: Context): Boolean = file(context).baseFile.exists()
    fun clear(context: Context) { file(context).delete() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun fingerprint(credential: String): String = MessageDigest.getInstance("SHA-256")
        .digest(credential.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun save(context: Context, username: String, role: String, credential: String, remember: Boolean) {
        clear(context)
        if (!remember) return
        require(username.isNotBlank() && credential.isNotBlank())
        val json = JSONObject().put("username", username).put("role", role)
            .put("credentialFingerprint", fingerprint(credential)).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val target = file(context)
        val stream = target.startWrite()
        try {
            stream.write(cipher.iv.size)
            stream.write(cipher.iv)
            stream.write(encrypted)
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }

    fun read(context: Context): Account? {
        if (!exists(context)) return null
        return try {
            val bytes = file(context).readFully()
            val ivLength = bytes.first().toInt()
            require(ivLength == 12 && bytes.size > ivLength + 17)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivLength)))
            }
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)), Charsets.UTF_8))
            Account(json.getString("username"), json.getString("role"), json.getString("credentialFingerprint"))
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    fun destination(context: Context, username: String, role: String): Intent = Intent(context, when {
        role == "Admin" -> AdminActivity::class.java
        role.equals("Station Owner", true) -> StationOwnerActivity::class.java
        else -> UserHomeActivity::class.java
    }).putExtra("USERNAME", username).putExtra("ACCOUNT_TYPE", role).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}
