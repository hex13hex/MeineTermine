package com.example.meinetermine

import java.time.ZoneId

object AppConfig {

    /** The application is intended for use in Germany. */
    val GERMANY_ZONE_ID: ZoneId = ZoneId.of("Europe/Berlin")

    // Через сколько после начала термина
    // его нужно автоматически удалить.
    //
    // 1 час: 1L * 60 * 60 * 1000
    // 5 минут: 5L * 60 * 1000
    // 30 минут: 30L * 60 * 1000
    const val AUTO_DELETE_AFTER_MILLIS =
        2L * 60 * 60 * 1000

    // На какой интервал термин блокирует время.
    //
    // Сейчас: 1 час.
    // Например:
    // 30 минут = 30L * 60 * 1000
    // 2 часа = 2L * 60 * 60 * 1000
    const val TERMIN_DURATION_MILLIS =
        1L * 60 * 60 * 1000
}
