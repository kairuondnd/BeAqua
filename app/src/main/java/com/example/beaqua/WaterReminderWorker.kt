package com.example.beaqua

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

class WaterReminderWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val intent = Intent(applicationContext, LoginActivity::class.java)
        NotificationHelper.showNotification(
            applicationContext,
            "Stay Hydrated!",
            "Check on your water supply, BeAqua is available for orders!",
            intent
        )
        return Result.success()
    }
}
