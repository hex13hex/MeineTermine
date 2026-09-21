package com.example.meinetermine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {

    fun scheduleReminders(
        context: Context,
        termin: TerminEntity
    ) {
        val appointmentTime = termin.scheduledAtMillis.takeIf { it > 0 }
            ?: DateTimeUtils.toEpochMillis(termin.date, termin.time)
            ?: return

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        //if (!alarmManager.canScheduleExactAlarms()) {
        //    return
        //}

        if (!alarmManager.canScheduleExactAlarms()) {
            android.util.Log.d(
                "ReminderScheduler",
                "EXACT ALARM НЕ РАЗРЕШЕН"
            )
            return
        }

        android.util.Log.d(
            "ReminderScheduler",
            "EXACT ALARM РАЗРЕШЕН"
        )

        val reminderOffsets = listOf(
            24L * 60 * 60 * 1000,
            8L * 60 * 60 * 1000,
            3L * 60 * 60 * 1000,
            1L * 60 * 60 * 1000
        )

        reminderOffsets.forEachIndexed { index, offset ->

            val reminderTime =
                appointmentTime - offset

            android.util.Log.d(
                "ReminderScheduler",
                "ПРОВЕРКА: reminderTime=$reminderTime, now=${System.currentTimeMillis()}"
            )

            if (reminderTime <= System.currentTimeMillis()) {
                android.util.Log.d(
                    "ReminderScheduler",
                    "ПРОПУСК: время напоминания уже прошло"
                )
                return@forEachIndexed
            }

            val intent = Intent(
                context,
                ReminderReceiver::class.java
            ).apply {
                putExtra("termin_id", termin.id)
                putExtra("description", termin.description)
                putExtra("date", termin.date)
                putExtra("time", termin.time)
                putExtra("reminder_index", index)
            }

            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    (termin.id * 10 + index).toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            android.util.Log.d(
                "ReminderScheduler",
                "ALARM УСТАНОВЛЕН: ${termin.date} ${termin.time}, " +
                        "reminderIndex=$index, " +
                        "reminderTime=$reminderTime"
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminderTime,
                pendingIntent
            )
        }
    }

    fun cancelReminders(
        context: Context,
        terminId: Long
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        repeat(4) { index ->

            val intent = Intent(
                context,
                ReminderReceiver::class.java
            )

            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    (terminId * 10 + index).toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun cancelAutoDelete(
        context: Context,
        terminId: Long
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(
            context,
            AutoDeleteReceiver::class.java
        )

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                (terminId * 100).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun scheduleAutoDelete(
        context: Context,
        termin: TerminEntity
    ) {
        val appointmentTime = termin.scheduledAtMillis.takeIf { it > 0 }
            ?: DateTimeUtils.toEpochMillis(termin.date, termin.time)
            ?: return

        // Через сколько после начала термина автоматически удалить его.
        val deleteAfterMillis = AppConfig.AUTO_DELETE_AFTER_MILLIS

        val deleteTime =
            appointmentTime + deleteAfterMillis

        if (deleteTime <= System.currentTimeMillis()) {
            return
        }

        val intent = Intent(
            context,
            AutoDeleteReceiver::class.java
        ).apply {
            putExtra("termin_id", termin.id)
        }

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                (termin.id * 100).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            deleteTime,
            pendingIntent
        )
    }
}
