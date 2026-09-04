package com.example.beaqua

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mapbox.common.MapboxOptions
import java.util.concurrent.TimeUnit

class BeAquaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
        NotificationHelper.createNotificationChannel(this)
        scheduleOrderMaintenance()
    }

    private fun scheduleOrderMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SubscriptionOrderWorker>(
            1,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly-subscription-orders",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
