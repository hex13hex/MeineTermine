package com.example.meinetermine

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object DriveBackupManager {

    private const val PREFERENCES_NAME = "drive_backup"
    private const val FOLDER_URI_KEY = "folder_uri"

    private const val PERIODIC_WORK_NAME = "drive_backup_periodic"
    private const val IMMEDIATE_WORK_NAME = "drive_backup_immediate"

    fun isConfigured(context: Context): Boolean {
        return getFolderUri(context) != null
    }

    fun getFolderUri(context: Context): Uri? {
        val value = context
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .getString(FOLDER_URI_KEY, null)

        return value?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
    }

    fun saveFolderUri(
        context: Context,
        uri: Uri
    ) {
        context
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(FOLDER_URI_KEY, uri.toString())
            .apply()

        schedulePeriodicBackup(context)
        enqueueImmediateBackup(context)
    }

    fun clearFolder(context: Context) {

        val uri = getFolderUri(context)

        if (uri != null) {

            val flags =
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    flags
                )
            }
        }

        context
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(FOLDER_URI_KEY)
            .apply()

        val workManager =
            WorkManager.getInstance(context)

        workManager.cancelUniqueWork(
            PERIODIC_WORK_NAME
        )

        workManager.cancelUniqueWork(
            IMMEDIATE_WORK_NAME
        )
    }

    fun getFolderName(context: Context): String {

        val uri =
            getFolderUri(context)
                ?: return "Не настроено"

        return runCatching {

            context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                        ?.takeIf { it.isNotBlank() }
                } else {
                    null
                }

            } ?: "Выбранная папка"

        }.getOrElse {
            "Выбранная папка"
        }
    }

    fun schedulePeriodicBackup(
        context: Context
    ) {

        if (!isConfigured(context)) {
            return
        }

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.CONNECTED
                )
                .build()

        val request =
            PeriodicWorkRequestBuilder<DriveBackupWorker>(
                AppConfig.DRIVE_BACKUP_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun enqueueImmediateBackup(
        context: Context
    ) {

        if (!isConfigured(context)) {
            return
        }

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.CONNECTED
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(constraints)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    suspend fun performBackup(
        context: Context
    ) {

        val treeUri =
            getFolderUri(context)
                ?: return

        val dao =
            TerminDatabase
                .getDatabase(context)
                .terminDao()

        val termine =
            dao.getAll()

        val csv =
            createCsv(termine)

        val fileName =
            createFileName()

        val resolver =
            context.contentResolver

        ensureDirectorySupportsCreate(
            resolver,
            treeUri
        )

        var createdFile: Uri? = null

        try {

            createdFile =
                DocumentsContract.createDocument(
                    resolver,
                    treeUri,
                    "text/csv",
                    fileName
                )
                    ?: throw IOException(
                        "Не удалось создать CSV-файл."
                    )

            resolver.openOutputStream(
                createdFile,
                "w"
            )?.use { output ->

                output.write(
                    csv.toByteArray(
                        Charsets.UTF_8
                    )
                )

                output.flush()

            } ?: throw IOException(
                "Не удалось открыть CSV-файл."
            )

        } catch (e: Exception) {

            if (createdFile != null) {

                runCatching {
                    DocumentsContract.deleteDocument(
                        resolver,
                        createdFile
                    )
                }
            }

            throw e
        }

        pruneOldBackups(
            resolver,
            treeUri
        )
    }

    private fun ensureDirectorySupportsCreate(
        resolver: android.content.ContentResolver,
        treeUri: Uri
    ) {

        val columns =
            arrayOf(
                DocumentsContract.Document.COLUMN_FLAGS
            )

        resolver.query(
            treeUri,
            columns,
            null,
            null,
            null
        )?.use { cursor ->

            if (!cursor.moveToFirst()) {
                throw IOException(
                    "Выбранная папка недоступна."
                )
            }

            val flags =
                cursor.getInt(0)

            if (
                flags and
                DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                == 0
            ) {
                throw IOException(
                    "В этой папке нельзя создавать файлы."
                )
            }

        } ?: throw IOException(
            "Не удалось проверить выбранную папку."
        )
    }

    private fun createFileName(): String {

        val formatter =
            SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss-SSS",
                Locale.US
            ).apply {

                timeZone =
                    TimeZone.getTimeZone(
                        "Europe/Berlin"
                    )
            }

        return AppConfig.DRIVE_BACKUP_FILE_PREFIX +
                formatter.format(Date()) +
                ".csv"
    }

    private fun createCsv(
        termine: List<TerminEntity>
    ): String {

        val builder =
            StringBuilder()

        appendCsvRow(
            builder,
            listOf(
                "id",
                "date",
                "time",
                "scheduled_at_millis",
                "description"
            )
        )

        termine.forEach { termin ->

            appendCsvRow(
                builder,
                listOf(
                    termin.id.toString(),
                    termin.date,
                    termin.time,
                    termin.scheduledAtMillis.toString(),
                    termin.description
                )
            )
        }

        return builder.toString()
    }

    private fun appendCsvRow(
        builder: StringBuilder,
        values: List<String>
    ) {

        builder
            .append(
                values.joinToString(",") { value ->
                    "\"${value.replace("\"", "\"\"")}\""
                }
            )
            .append("\r\n")
    }

    private fun pruneOldBackups(
        resolver: android.content.ContentResolver,
        treeUri: Uri
    ) {

        val directoryId =
            DocumentsContract.getTreeDocumentId(
                treeUri
            )

        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                directoryId
            )

        val files =
            mutableListOf<Pair<String, Uri>>()

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )

            val nameIndex =
                cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )

            while (cursor.moveToNext()) {

                if (
                    idIndex < 0 ||
                    nameIndex < 0
                ) {
                    break
                }

                val documentId =
                    cursor.getString(idIndex)

                val displayName =
                    cursor.getString(nameIndex)
                        ?: continue

                if (
                    displayName.startsWith(
                        AppConfig.DRIVE_BACKUP_FILE_PREFIX
                    ) &&
                    displayName.endsWith(".csv")
                ) {

                    files +=
                        displayName to
                                DocumentsContract
                                    .buildDocumentUriUsingTree(
                                        treeUri,
                                        documentId
                                    )
                }
            }
        }

        files
            .sortedByDescending { it.first }
            .drop(
                AppConfig.DRIVE_BACKUP_MAX_FILES
            )
            .forEach { (_, uri) ->

                runCatching {

                    DocumentsContract.deleteDocument(
                        resolver,
                        uri
                    )
                }
            }
    }
}