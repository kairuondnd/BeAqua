package com.example.beaqua

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Account-scoped reminders also run when the customer screen is closed. */
class DeliveryReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val username = inputData.getString("username") ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("delivery_reminders", Context.MODE_PRIVATE)
        if (prefs.getString("customer", null) != username) return Result.success()
        return try {
            val subscriptions = Tasks.await(FirebaseHelper.subscriptionsCollection
                .whereEqualTo("customerUsername", username).get(Source.SERVER), 30, TimeUnit.SECONDS)
            val stations = mutableMapOf<String, User>()
            for (document in subscriptions.documents) {
                if (isStopped || prefs.getString("customer", null) != username) break
                val delivery = document.toObject(WeeklySubscription::class.java) ?: continue
                if (!delivery.active || delivery.offeringType != OFFERING_PURCHASE) continue
                val items = delivery.deliveryItems()
                if (items.isEmpty()) continue
                val key = "$username:${document.id}:${delivery.nextDeliveryAt}"
                if (prefs.getBoolean(key, false)) continue
                val station = stations[delivery.stationOwnerUsername] ?: Tasks.await(
                    FirebaseHelper.usersCollection.document(delivery.stationOwnerUsername).get(Source.SERVER),
                    30, TimeUnit.SECONDS).toObject(User::class.java)?.also {
                        stations[delivery.stationOwnerUsername] = it
                    } ?: continue
                if (!station.isApprovedStationOwner()) continue
                if (!DeliveryFinalization.shouldRemind(delivery.nextDeliveryAt, station.operatingHours, System.currentTimeMillis())) continue
                val deadline = SimpleDateFormat("MMM d, h:mm a z", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone(station.operatingHours.timeZoneId)
                }.format(Date(DeliveryFinalization.cutoff(delivery.nextDeliveryAt, station.operatingHours)))
                if (prefs.getString("customer", null) != username) break
                val shown = NotificationHelper.showNotification(applicationContext,
                    "Finalize your automated delivery",
                    "${station.name}: review ${items.sumOf { it.quantity }} unit(s) across ${items.size} item(s). Edit selections or quantities before $deadline. Tap to review.",
                    Intent(applicationContext, SubscriptionActivity::class.java)
                        .putExtra("USERNAME", username)
                        .putExtra("REMINDER_SUBSCRIPTION_ID", document.id)
                        .putExtra("REMINDER_DELIVERY_AT", delivery.nextDeliveryAt),
                    notificationId = key.hashCode())
                if (shown) prefs.edit().putBoolean(key, true).commit()
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    companion object {
        fun start(context: Context, username: String) {
            context.getSharedPreferences("delivery_reminders", Context.MODE_PRIVATE)
                .edit().putString("customer", username).apply()
            val data = workDataOf("username" to username)
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork("delivery-finalization-reminders", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DeliveryReminderWorker>(15, TimeUnit.MINUTES)
                    .setInputData(data).setConstraints(constraints).build())
            manager.enqueueUniqueWork("delivery-finalization-check", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DeliveryReminderWorker>()
                    .setInputData(data).setConstraints(constraints).build())
        }

        fun clear(context: Context) {
            context.getSharedPreferences("delivery_reminders", Context.MODE_PRIVATE)
                .edit().remove("customer").apply()
            WorkManager.getInstance(context).cancelUniqueWork("delivery-finalization-reminders")
            WorkManager.getInstance(context).cancelUniqueWork("delivery-finalization-check")
        }
    }
}
