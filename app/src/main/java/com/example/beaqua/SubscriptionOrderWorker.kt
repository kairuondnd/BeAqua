package com.example.beaqua

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class SubscriptionOrderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences("order_maintenance", Context.MODE_PRIVATE)
        val username = preferences.getString("username", null) ?: return Result.success()
        val stationOwner = preferences.getBoolean("stationOwner", false)
        return try {
            Tasks.await(FirebaseHelper.processDueSubscriptions(
                customerUsername = if (stationOwner) null else username,
                stationOwnerUsername = if (stationOwner) username else null), 60, TimeUnit.SECONDS)
            Tasks.await(FirebaseHelper.cancelExpiredPendingOrders(
                customerUsername = if (stationOwner) null else username,
                stationOwnerUsername = if (stationOwner) username else null), 60, TimeUnit.SECONDS)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun bindAccount(context: Context, username: String, stationOwner: Boolean) {
            context.getSharedPreferences("order_maintenance", Context.MODE_PRIVATE).edit()
                .putString("username", username).putBoolean("stationOwner", stationOwner).apply()
            WorkManager.getInstance(context).enqueueUniqueWork("account-order-maintenance",
                ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SubscriptionOrderWorker>()
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build())
        }

        fun clearAccount(context: Context) {
            context.getSharedPreferences("order_maintenance", Context.MODE_PRIVATE).edit().clear().apply()
            WorkManager.getInstance(context).cancelUniqueWork("account-order-maintenance")
        }
    }
}
