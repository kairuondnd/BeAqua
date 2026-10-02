package com.example.beaqua

import android.content.Context
import android.widget.*
import androidx.appcompat.app.AlertDialog

object AdminAccountDirectory {
    fun show(context: Context) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val status = TextView(context).apply { text = "Loading accounts…" }
        content.addView(status)
        val dialog = AlertDialog.Builder(context).setTitle("User accounts")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton("Close", null).create()
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        FirebaseHelper.getAllUsers().addOnSuccessListener { snapshot ->
            if (!dialog.isShowing) return@addOnSuccessListener
            val users = snapshot.toObjects(User::class.java).sortedBy { it.username.lowercase() }
            status.text = if (users.isEmpty()) "No accounts found." else "${users.size} accounts"
            users.forEach { user ->
                content.addView(TextView(context).apply {
                    text = "\n${user.name}\nUsername: ${user.username}\nAccount type: ${user.accountType}"
                    textSize = 16f
                })
                val password = TextView(context).apply {
                    text = when {
                        user.password.isBlank() -> "Password: Not set"
                        PasswordHelper.isHashed(user.password) -> "Password: Hashed — original password cannot be viewed."
                        else -> "Password: ••••••••"
                    }
                }
                content.addView(password)
                if (user.password.isNotBlank() && !PasswordHelper.isHashed(user.password)) {
                    content.addView(Button(context).apply {
                        text = "Show password"
                        var revealed = false
                        setOnClickListener {
                            revealed = !revealed
                            password.text = if (revealed) "Password: ${user.password}" else "Password: ••••••••"
                            text = if (revealed) "Hide password" else "Show password"
                        }
                    })
                }
            }
        }.addOnFailureListener {
            if (dialog.isShowing) status.text = "Could not load accounts. Close and try again."
        }
    }
}
