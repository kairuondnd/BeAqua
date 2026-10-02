package com.example.beaqua

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore

object PasswordRecovery {
    val requests get() = FirebaseFirestore.getInstance().collection("passwordRecoveryRequests")

    fun request(context: Context, initialUsername: String) {
        val input = EditText(context).apply { hint = "Your username"; setText(initialUsername); isSingleLine = true }
        val dialog = AlertDialog.Builder(context).setTitle("Forgot password?")
            .setMessage("Enter your username to ask the administrator for help. They will use the email or phone number registered to your account after verifying your identity.")
            .setView(input).setPositiveButton("Request help", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = input.text.toString().trim()
                if (username.isBlank() || username.contains('/') || username == AdminChat.USERNAME) {
                    input.error = "Enter a valid customer or station username."; return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                // One outstanding request per account prevents repeated taps creating duplicate tickets.
                FirebaseFirestore.getInstance().runTransaction { tx ->
                    val user = tx.get(FirebaseHelper.usersCollection.document(username)).toObject(User::class.java)
                    if (user != null) {
                        val ref = requests.document(username)
                        val existing = tx.get(ref)
                        if (existing.getString("status") != "Pending") {
                            tx.set(ref, mapOf("username" to username, "status" to "Pending",
                                "createdAt" to System.currentTimeMillis()))
                        }
                    }
                    null
                }.addOnSuccessListener {
                    dialog.dismiss()
                    AlertDialog.Builder(context).setTitle("Recovery request submitted")
                        .setMessage("If the username matches an account, the administrator has been asked to help. Please check your registered email and phone messages for their response. Recovery is handled manually and may take time.")
                        .setPositiveButton("OK", null).show()
                }.addOnFailureListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    input.error = "Could not submit the request. Check your connection and try again."
                }
            }
        }
        dialog.show()
    }

    fun inbox(context: Context) {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val status = TextView(context).apply { text = "Loading requests…" }
        list.addView(status)
        val dialog = AlertDialog.Builder(context).setTitle("Password recovery requests")
            .setView(ScrollView(context).apply { addView(list) }).setPositiveButton("Close", null).show()
        val listener = requests.whereEqualTo("status", "Pending").addSnapshotListener { snapshot, error ->
            list.removeAllViews()
            list.addView(status)
            status.text = when {
                error != null -> "Could not load requests. Close and try again."
                snapshot == null || snapshot.isEmpty -> "No pending recovery requests."
                else -> "Mark a request resolved after helping the user recover account access."
            }
            snapshot?.documents?.sortedByDescending { it.getLong("createdAt") ?: 0L }?.forEach { doc ->
                val username = doc.getString("username").orEmpty()
                list.addView(Button(context).apply {
                    text = "$username — Forgot password"
                    setOnClickListener {
                        AlertDialog.Builder(context).setTitle("Recovery request: $username")
                            .setMessage("Mark this request resolved only after you have helped the user recover access.")
                            .setPositiveButton("Mark resolved") { _, _ ->
                                doc.reference.update("status", "Resolved").addOnFailureListener {
                                    Toast.makeText(context, "Could not update request. Please try again.", Toast.LENGTH_LONG).show()
                                }
                            }
                            .setNegativeButton("Cancel", null).show()
                    }
                })
            }
        }
        dialog.setOnDismissListener { listener.remove() }
    }
}
