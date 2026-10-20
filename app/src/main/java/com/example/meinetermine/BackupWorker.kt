package com.example.meinetermine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        if (!DriveBackupManager.isConfigured(applicationContext)) {
            // The periodic job is registered at first app launch. It becomes
            // active after the user chooses a Drive folder once.
            return Result.success()
        }

        return try {
            DriveBackupManager.performBackup(applicationContext)
            Result.success()
        } catch (e: SecurityException) {
            // The folder was revoked; retrying cannot restore its permission.
            Result.failure()
        } catch (e: Exception) {
            // Network/provider failures are transient and WorkManager backs off.
            Result.retry()
        }
    }
}
