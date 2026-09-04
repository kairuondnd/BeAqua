package com.example.beaqua

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration

object NotificationHelper {
    private const val CHANNEL_ID = "beaqua_notifications"
    private const val CHANNEL_NAME = "BeAqua Notifications"
    private const val CHANNEL_DESC = "Notifications for orders and updates"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        intent: Intent? = null
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val pendingIntent: PendingIntent? = intent?.let {
            PendingIntent.getActivity(
                context,
                (title + message).hashCode(),
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.beaqua) // Ensure this icon exists
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        return try {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun listenForAccountNotifications(
        context: Context,
        recipientUsername: String,
        destinationIntent: (BeAquaNotification) -> Intent
    ): ListenerRegistration {
        return FirebaseHelper.notificationsCollection
            .whereEqualTo("recipientUsername", recipientUsername)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .map { it.document.toObject(BeAquaNotification::class.java) }
                    .filter { it.deliveredAt <= 0L }
                    .forEach { notification ->
                        val shown = showNotification(
                            context,
                            notification.title,
                            notification.message,
                            destinationIntent(notification)
                        )
                        if (shown) {
                            FirebaseHelper.markNotificationDelivered(notification.id)
                        }
                    }
            }
    }
}
