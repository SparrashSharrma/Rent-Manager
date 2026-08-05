package com.rentmanager.app.worker
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Scheduled due rent check and push notification trigger
        return Result.success()
    }
}
