package com.example.meinetermine

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

object TelegramReportScheduler {

    private const val DAILY_WORK_NAME = "MeineTermine_Telegram_Daily_Report"
    private const val MANUAL_WORK_NAME = "MeineTermine_Telegram_Manual_Send"
    private const val TIME_ZONE = "Europe/Berlin"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            createRequest(manual = false)
        )
    }

    fun scheduleNext(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            createRequest(manual = false)
        )
    }

    /**
     * Queues an immediate manual report. Returns the request ID so the UI can
     * observe the result through WorkManager.
     */
    fun sendNow(context: Context): UUID {
        val request = createRequest(manual = true)
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    private fun createRequest(manual: Boolean) =
        OneTimeWorkRequestBuilder<TelegramReportWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean("manual_send", manual)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .apply {
                if (!manual) {
                    setInitialDelay(delayUntilNextReport(), TimeUnit.MILLISECONDS)
                }
            }
            .build()

    private fun delayUntilNextReport(): Long {
        val zone = TimeZone.getTimeZone(TIME_ZONE)
        val now = Calendar.getInstance(zone)
        val target = Calendar.getInstance(zone).apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, AppConfig.TELEGRAM_REPORT_HOUR)
            set(Calendar.MINUTE, AppConfig.TELEGRAM_REPORT_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
}
