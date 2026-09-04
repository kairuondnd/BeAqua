package com.example.beaqua

import android.content.Context

object AdminCredentialStore {
    private const val PREFERENCES = "admin_credentials"
    private const val PASSWORD_HASH = "password_hash"
    private const val DEFAULT_PASSWORD = "admin123"

    fun verify(context: Context, password: String): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        var storedHash = preferences.getString(PASSWORD_HASH, null)
        if (storedHash.isNullOrBlank()) {
            storedHash = PasswordHelper.hash(DEFAULT_PASSWORD)
            preferences.edit().putString(PASSWORD_HASH, storedHash).apply()
        }
        return PasswordHelper.verify(password, storedHash)
    }

    fun changePassword(context: Context, newPassword: String) {
        require(newPassword.length >= 6) { "Admin password must contain at least 6 characters" }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(PASSWORD_HASH, PasswordHelper.hash(newPassword))
            .apply()
    }
}
