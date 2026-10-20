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

    /** Parses the CSV files produced by the automatic Google Drive backup. */
    fun parseCsv(csv: String): List<TerminBackup> {
        val rows = parseCsvRows(csv.removePrefix("\uFEFF"))
            .filter { row -> row.any { it.isNotBlank() } }
        require(rows.isNotEmpty()) { "CSV is empty" }

        val columns = rows.first().mapIndexed { index, name -> name.trim() to index }.toMap()
        val dateIndex = columns["date"] ?: error("Missing date column")
        val timeIndex = columns["time"] ?: error("Missing time column")
        val descriptionIndex = columns["description"] ?: error("Missing description column")
        val idIndex = columns["id"]

        return rows.drop(1).map { row ->
            fun value(index: Int): String = row.getOrElse(index) { "" }
            TerminBackup(
                id = idIndex?.let(::value)?.toLongOrNull() ?: 0L,
                date = value(dateIndex),
                time = value(timeIndex),
                description = value(descriptionIndex)
            )
        }
    }

    private fun parseCsvRows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        while (index < input.length) {
            when (val character = input[index]) {
                '"' -> {
                    if (quoted && input.getOrNull(index + 1) == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = !quoted
                    }
                }
                ',' -> if (quoted) field.append(character) else {
                    row += field.toString()
                    field.clear()
                }
                '\n' -> if (quoted) field.append(character) else {
                    row += field.toString().removeSuffix("\r")
                    rows += row.toList()
                    row.clear()
                    field.clear()
                }
                else -> field.append(character)
            }
            index++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString().removeSuffix("\r")
            rows += row.toList()
        }
        return rows
    }
}
