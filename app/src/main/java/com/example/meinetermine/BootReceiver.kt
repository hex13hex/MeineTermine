package com.example.meinetermine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        android.util.Log.d(
            "BootReceiver",
            "BOOT_COMPLETED ПОЛУЧЕН"
        )

        val pendingResult = goAsync()

        val database =
            TerminDatabase.getDatabase(context)

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val dao = database.terminDao()
                TerminMaintenance.migrateLegacyTimestamps(dao)
                val termine = dao.getAll()

                BackupScheduler.schedule(context)

                // Автоудаление термина
                val deleteAfterMillis = AppConfig.AUTO_DELETE_AFTER_MILLIS

                for (termin in termine) {

                    val appointmentTime = termin.scheduledAtMillis.takeIf { it > 0 }
                        ?: DateTimeUtils.toEpochMillis(termin.date, termin.time)
                        ?: continue

                    val deleteTime =
                        appointmentTime + deleteAfterMillis

                    android.util.Log.d(
                        "BootReceiver",
                        "ТЕРМИН: id=${termin.id}, " +
                                "${termin.date} ${termin.time}, " +
                                "deleteTime=$deleteTime, " +
                                "now=${System.currentTimeMillis()}"
                    )

                    // Время автоудаления уже наступило.
                    if (
                        System.currentTimeMillis() >= deleteTime
                    ) {

                        database
                            .terminDao()
                            .delete(termin)

                        android.util.Log.d(
                            "BootReceiver",
                            "ТЕРМИН УДАЛЁН ПОСЛЕ BOOT: id=${termin.id}"
                        )

                        continue
                    }

                    // Термин ещё не начался.
                    // Восстанавливаем напоминания.
                    if (
                        appointmentTime >
                        System.currentTimeMillis()
                    ) {

                        ReminderScheduler.cancelReminders(
                            context,
                            termin.id
                        )

                        ReminderScheduler.scheduleReminders(
                            context,
                            termin
                        )
                    }

                    // В любом случае, если время автоудаления
                    // ещё не наступило — восстанавливаем AutoDelete.
                    ReminderScheduler.cancelAutoDelete(
                        context,
                        termin.id
                    )

                    ReminderScheduler.scheduleAutoDelete(
                        context,
                        termin
                    )

                    android.util.Log.d(
                        "BootReceiver",
                        "AUTO DELETE ВОССТАНОВЛЕН: id=${termin.id}"
                    )
                }

            } finally {
                pendingResult.finish()
            }
        }
    }
}
