package com.example.beaqua

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks

class SubscriptionOrderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        return try {
            Tasks.await(FirebaseHelper.processDueSubscriptions())
            Tasks.await(FirebaseHelper.cancelExpiredPendingOrders())
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
