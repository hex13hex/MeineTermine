package com.example.meinetermine

import android.content.Context

/** Keeps persisted appointments and their OS alarms consistent. */
object TerminMaintenance {
    suspend fun migrateLegacyTimestamps(dao: TerminDao) {
        dao.getLegacyTermine().forEach { termin ->
            DateTimeUtils.toEpochMillis(termin.date, termin.time)?.let {
                dao.updateScheduledAt(termin.id, it)
            }
        }
    }

    suspend fun scheduleAllUpcoming(context: Context, dao: TerminDao) {
        migrateLegacyTimestamps(dao)
        dao.getAll().filter { it.scheduledAtMillis > System.currentTimeMillis() }.forEach {
            ReminderScheduler.scheduleReminders(context, it)
            ReminderScheduler.scheduleAutoDelete(context, it)
        }
    }

    fun cancelAll(context: Context, termine: List<TerminEntity>) {
        termine.forEach {
            ReminderScheduler.cancelReminders(context, it.id)
            ReminderScheduler.cancelAutoDelete(context, it.id)
        }
    }
}
