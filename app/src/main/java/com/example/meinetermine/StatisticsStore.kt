package com.example.meinetermine

import android.content.Context

/** Persists statistics independently from appointments that are later deleted. */
class StatisticsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getTotalEverCreated(currentAppointmentsCount: Int): Long {
        if (!preferences.contains(TOTAL_CREATED)) {
            // Existing installations start their history from the appointments
            // that were already present before statistics was introduced.
            preferences.edit().putLong(TOTAL_CREATED, currentAppointmentsCount.toLong()).apply()
        }
        return preferences.getLong(TOTAL_CREATED, currentAppointmentsCount.toLong())
    }

    fun addCreated(count: Int) {
        if (count <= 0) return
        val total = preferences.getLong(TOTAL_CREATED, 0L)
        preferences.edit().putLong(TOTAL_CREATED, total + count).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "appointment_statistics"
        const val TOTAL_CREATED = "total_ever_created"
    }
}
