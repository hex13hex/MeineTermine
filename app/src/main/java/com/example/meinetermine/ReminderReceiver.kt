package com.example.meinetermine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        android.util.Log.d(
            "ReminderReceiver",
            "REMINDER RECEIVER ЗАПУЩЕН: приложение закрыто или открыто — неважно"
        )

        val description =
            intent.getStringExtra("description") ?: "Термин"

        val date =
            intent.getStringExtra("date") ?: ""

        val time =
            intent.getStringExtra("time") ?: ""

        val reminderIndex =
            intent.getIntExtra("reminder_index", 0)

        val reminderText = when (reminderIndex) {
            0 -> "Через 24 часа"
            1 -> "Через 8 часов"
            2 -> "Через 3 часа"
            else -> "Через 1 час"
        }

        NotificationHelper.showNotification(
            context = context,
            title = "$reminderText: $description",
            message = "$date в $time"
        )
    }
}