package com.example.meinetermine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoDeleteReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val terminId =
            intent.getLongExtra("termin_id", -1L)

        if (terminId == -1L) {
            return
        }

        val pendingResult = goAsync()

        val database =
            TerminDatabase.getDatabase(context)

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val termin =
                    database.terminDao().getById(terminId)

                if (termin != null) {

                    val appointmentTime = termin.scheduledAtMillis.takeIf { it > 0 }
                        ?: DateTimeUtils.toEpochMillis(termin.date, termin.time)
                        ?: return@launch

                    // Через сколько после начала термина его удалить.
                    val deleteAfterMillis = AppConfig.AUTO_DELETE_AFTER_MILLIS

                    val deleteTime =
                        appointmentTime + deleteAfterMillis

                    if (
                        System.currentTimeMillis() >= deleteTime
                    ) {
                        database
                            .terminDao()
                            .delete(termin)

                        android.util.Log.d(
                            "AutoDeleteReceiver",
                            "ТЕРМИН УДАЛЁН: id=${termin.id}"
                        )
                    }

                }

            } finally {
                pendingResult.finish()
            }
        }
    }
}
