package com.example.meinetermine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        // Проверяем наличие интернета.
        val connectivityManager =
            applicationContext.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network =
            connectivityManager.activeNetwork

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)

        val hasInternet =
            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true

        if (!hasInternet) {
            return Result.retry()
        }

        // Получаем базу данных и DAO.
        val database =
            TerminDatabase.getDatabase(applicationContext)

        val dao =
            database.terminDao()

        // Пытаемся получить уже выданный Google Drive access token
        // без показа окна авторизации.
        val accessToken =
            GoogleDriveAuth().getAccessTokenSilently(
                applicationContext
            )

        if (accessToken == null) {
            return Result.retry()
        }

        // Создаём и загружаем резервную копию.
        val success =
            GoogleDriveBackup.createBackup(
                dao = dao,
                accessToken = accessToken
            )

        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}