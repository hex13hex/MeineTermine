package com.example.meinetermine

import com.google.gson.Gson

object BackupManager {

    fun createJson(termine: List<TerminEntity>): String {
        val backup = termine.map { termin ->
            TerminBackup(
                id = termin.id,
                date = termin.date,
                time = termin.time,
                description = termin.description
            )
        }

        return Gson().toJson(backup)
    }

    fun parseJson(json: String): List<TerminBackup> {
        val type = object : com.google.gson.reflect.TypeToken<List<TerminBackup>>() {}.type

        return Gson().fromJson(json, type)
    }
}