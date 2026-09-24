package com.example.meinetermine

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

object GoogleDriveBackup {

    private const val FOLDER_NAME = "MeineTermine Backup"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val CSV_MIME = "text/csv"

    suspend fun createBackup(
        dao: TerminDao,
        accessToken: String
    ): Boolean {

        return try {

            val termine = dao.getAll()

            val csv = createCsv(termine)

            val folderId =
                findOrCreateFolder(accessToken)

            val fileName =
                createFileName()

            uploadCsv(
                accessToken = accessToken,
                folderId = folderId,
                fileName = fileName,
                csv = csv
            )

            // Новая копия уже успешно загружена.
            // Только теперь удаляем старые копии.
            deleteOldBackups(
                accessToken = accessToken,
                folderId = folderId
            )

            true

        } catch (e: Exception) {

            false
        }
    }

    private fun createCsv(termine: List<TerminEntity>): String {

        val builder = StringBuilder()

        // BOM — чтобы Excel корректно показывал русский текст.
        builder.append('\uFEFF')

        builder.appendLine(
            "date,time,scheduledAtMillis,description"
        )

        for (termin in termine) {

            builder.append(csvValue(termin.date))
                .append(",")

            builder.append(csvValue(termin.time))
                .append(",")

            builder.append(csvValue(termin.scheduledAtMillis.toString()))
                .append(",")

            builder.append(csvValue(termin.description))
                .appendLine()
        }

        return builder.toString()
    }

    private fun csvValue(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun createFileName(): String {

        val formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        return "termin_backup_${
            ZonedDateTime.now(
                ZoneId.of("Europe/Berlin")
            ).format(formatter)
        }.csv"
    }

    private fun findOrCreateFolder(
        accessToken: String
    ): String {

        val query = "'root' in parents " +
                "and name = '$FOLDER_NAME' " +
                "and mimeType = '$FOLDER_MIME' " +
                "and trashed = false"

        val encodedQuery =
            URLEncoder.encode(
                query,
                Charsets.UTF_8.name()
            )

        val url = URL(
            "https://www.googleapis.com/drive/v3/files" +
                    "?q=$encodedQuery" +
                    "&spaces=drive" +
                    "&pageSize=10" +
                    "&fields=files(id,name)"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "GET"

        connection.setRequestProperty(
            "Authorization",
            "Bearer $accessToken"
        )

        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        val responseCode = connection.responseCode

        if (responseCode != HttpURLConnection.HTTP_OK) {

            throw Exception(
                "Не удалось найти папку. HTTP $responseCode"
            )
        }

        val response =
            connection.inputStream
                .bufferedReader()
                .use { it.readText() }

        connection.disconnect()

        val files =
            JSONObject(response)
                .optJSONArray("files")

        if (files != null && files.length() > 0) {

            return files
                .getJSONObject(0)
                .getString("id")
        }

        return createFolder(accessToken)
    }

    private fun createFolder(
        accessToken: String
    ): String {

        val url =
            URL(
                "https://www.googleapis.com/drive/v3/files"
            )

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.doOutput = true

        connection.setRequestProperty(
            "Authorization",
            "Bearer $accessToken"
        )

        connection.setRequestProperty(
            "Content-Type",
            "application/json; charset=UTF-8"
        )

        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        val body =
            JSONObject().apply {
                put("name", FOLDER_NAME)
                put("mimeType", FOLDER_MIME)
            }.toString()

        connection.outputStream.use {
            it.write(body.toByteArray(Charsets.UTF_8))
        }

        val responseCode = connection.responseCode

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception(
                "Не удалось создать папку. HTTP $responseCode"
            )
        }

        val response =
            connection.inputStream
                .bufferedReader()
                .use { it.readText() }

        connection.disconnect()

        return JSONObject(response)
            .getString("id")
    }

    private fun uploadCsv(
        accessToken: String,
        folderId: String,
        fileName: String,
        csv: String
    ) {

        val boundary =
            "MeineTermineBoundary${System.currentTimeMillis()}"

        val url =
            URL(
                "https://www.googleapis.com/upload/drive/v3/files" +
                        "?uploadType=multipart"
            )

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.doOutput = true

        connection.setRequestProperty(
            "Authorization",
            "Bearer $accessToken"
        )

        connection.setRequestProperty(
            "Content-Type",
            "multipart/related; boundary=$boundary"
        )

        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000

        val metadata =
            JSONObject().apply {
                put("name", fileName)
                put("mimeType", CSV_MIME)

                put(
                    "parents",
                    JSONArray().put(folderId)
                )
            }.toString()

        val output =
            connection.outputStream

        output.use {

            it.write(
                "--$boundary\r\n".toByteArray()
            )

            it.write(
                "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                    .toByteArray()
            )

            it.write(
                metadata.toByteArray(Charsets.UTF_8)
            )

            it.write(
                "\r\n--$boundary\r\n".toByteArray()
            )

            it.write(
                "Content-Type: $CSV_MIME; charset=UTF-8\r\n\r\n"
                    .toByteArray()
            )

            it.write(
                csv.toByteArray(Charsets.UTF_8)
            )

            it.write(
                "\r\n--$boundary--\r\n".toByteArray()
            )
        }

        val responseCode = connection.responseCode

        if (
            responseCode != HttpURLConnection.HTTP_OK &&
            responseCode != HttpURLConnection.HTTP_CREATED
        ) {
            throw Exception(
                "Ошибка загрузки CSV. HTTP $responseCode"
            )
        }

        connection.disconnect()
    }

    private fun deleteOldBackups(
        accessToken: String,
        folderId: String
    ) {
        val query =
            "'$folderId' in parents " +
                    "and name contains 'termin_backup_' " +
                    "and trashed = false"

        val encodedQuery =
            URLEncoder.encode(
                query,
                Charsets.UTF_8.name()
            )

        val url = URL(
            "https://www.googleapis.com/drive/v3/files" +
                    "?q=$encodedQuery" +
                    "&orderBy=createdTime desc" +
                    "&pageSize=100" +
                    "&fields=files(id,name,createdTime)"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "GET"

        connection.setRequestProperty(
            "Authorization",
            "Bearer $accessToken"
        )

        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        val responseCode = connection.responseCode

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception(
                "Не удалось получить список резервных копий. HTTP $responseCode"
            )
        }

        val response =
            connection.inputStream
                .bufferedReader()
                .use { it.readText() }

        connection.disconnect()

        val files =
            JSONObject(response)
                .optJSONArray("files")
                ?: return

        // Оставляем 5 самых новых файлов.
        for (index in 5 until files.length()) {

            val fileId =
                files
                    .getJSONObject(index)
                    .getString("id")

            deleteFile(
                accessToken = accessToken,
                fileId = fileId
            )
        }
    }

    private fun deleteFile(
        accessToken: String,
        fileId: String
    ) {
        val url =
            URL(
                "https://www.googleapis.com/drive/v3/files/$fileId"
            )

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "DELETE"

        connection.setRequestProperty(
            "Authorization",
            "Bearer $accessToken"
        )

        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        val responseCode = connection.responseCode

        if (responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
            throw Exception(
                "Не удалось удалить старую копию. HTTP $responseCode"
            )
        }

        connection.disconnect()
    }
}