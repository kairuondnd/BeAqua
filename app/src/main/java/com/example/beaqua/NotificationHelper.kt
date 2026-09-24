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
        intent: Intent? = null,
        notificationId: Int = System.currentTimeMillis().toInt()
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val pendingIntent: PendingIntent? = intent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.beaqua) // Ensure this icon exists
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

        return try {
            manager.notify(notificationId, builder.build())
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
                    .map { change -> change.document.toObject(BeAquaNotification::class.java).apply { id = change.document.id } }
                    .filter { it.deliveredAt <= 0L }
                    .forEach { notification ->
                        val shown = showNotification(
                            context,
                            notification.title,
                            notification.message,
                            destinationIntent(notification),
                            notificationId = notification.id.hashCode()
                        )
                        if (shown) {
                            FirebaseHelper.markNotificationDelivered(notification.id)
                        }
                    }
            }
    }

    fun customerDestination(context: Context, username: String, notification: BeAquaNotification): Intent =
        if (notification.type == BeAquaNotification.TYPE_ORDER_DELIVERED ||
            notification.type == BeAquaNotification.TYPE_RECEIPT_UPDATED) {
            ReceiptActivity.intent(context, username, notification.orderId)
        } else {
            Intent(context, UserHistoryActivity::class.java).putExtra("USERNAME", username)
        }

    fun deliverPendingCustomerNotifications(context: Context, username: String): com.google.android.gms.tasks.Task<Void> {
        return FirebaseHelper.notificationsCollection.whereEqualTo("recipientUsername", username)
            .get(com.google.firebase.firestore.Source.SERVER).continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Could not load notifications")
                val session = context.getSharedPreferences("order_maintenance", Context.MODE_PRIVATE)
                if (session.getString("username", null) != username || session.getBoolean("stationOwner", false)) {
                    return@continueWithTask com.google.android.gms.tasks.Tasks.forResult<Void>(null)
                }
                val updates = task.result.documents.mapNotNull { document ->
                    val notification = document.toObject(BeAquaNotification::class.java) ?: return@mapNotNull null
                    if (notification.deliveredAt > 0) return@mapNotNull null
                    notification.id = document.id
                    if (showNotification(context, notification.title, notification.message,
                            customerDestination(context, username, notification), notification.id.hashCode())) {
                        FirebaseHelper.markNotificationDelivered(notification.id)
                    } else null
                }
                com.google.android.gms.tasks.Tasks.whenAll(updates)
            }
    }
}
