package com.example.meinetermine

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {

    override suspend fun doWork(): Result {

        if (
            !DriveBackupManager.isConfigured(
                applicationContext
            )
        ) {
            return Result.success()
        }

        return try {

            DriveBackupManager.performBackup(
                applicationContext
            )

            Log.d(
                "DriveBackupWorker",
                "Google Drive backup выполнен"
            )

            Result.success()

        } catch (e: SecurityException) {

            Log.e(
                "DriveBackupWorker",
                "Нет доступа к выбранной папке",
                e
            )

            Result.failure()

        } catch (e: Exception) {

            Log.e(
                "DriveBackupWorker",
                "Ошибка Google Drive backup",
                e
            )

            Result.retry()
        }
    }
}