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
        1L * 60 * 60 * 1000

    // На какой интервал термин блокирует время.
    //
    // Сейчас: 1 час.
    // Например:
    // 30 минут = 30L * 60 * 1000
    // 2 часа = 2L * 60 * 60 * 1000
    const val TERMIN_DURATION_MILLIS =
        1L * 60 * 60 * 1000

    // Сколько последних backup-файлов хранить.
    const val DRIVE_BACKUP_MAX_FILES = 5

    // По этому префиксу приложение отличает
    // свои backup-файлы от других файлов в папке.
    const val DRIVE_BACKUP_FILE_PREFIX = "termin_backup_"

    // Интервал автоматического резервного копирования в Google Drive.
    // WorkManager выполняет периодические задачи приблизительно: система может
    // отложить запуск для экономии батареи или при отсутствии сети.
    const val BACKUP_INTERVAL_MINUTES = 30L
}
