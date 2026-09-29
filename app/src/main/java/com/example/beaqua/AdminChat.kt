package com.example.beaqua

import android.content.Context
import android.content.Intent

/** The app's existing reserved administrator login also identifies its support inbox. */
object AdminChat {
    const val USERNAME = "admin"
    fun contact() = User(username = USERNAME, name = "BeAqua Admin", accountType = "Admin")
    fun open(context: Context, currentUsername: String, partnerUsername: String) {
        context.startActivity(Intent(context, SingleChatActivity::class.java)
            .putExtra("CURRENT_USERNAME", currentUsername)
            .putExtra("CHAT_WITH_USERNAME", partnerUsername))
    }
}
