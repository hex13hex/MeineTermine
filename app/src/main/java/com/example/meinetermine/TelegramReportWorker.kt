package com.example.meinetermine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class TelegramReportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val manualSend = inputData.getBoolean("manual_send", false)

        if (AppConfig.TELEGRAM_BOT_TOKEN.isBlank() ||
            AppConfig.TELEGRAM_RECIPIENT_IDS.isEmpty()
        ) {
            if (manualSend) {
                return Result.failure(
                    workDataOf("error" to "Не настроен токен бота или список получателей.")
                )
            }

            // Keep the daily schedule alive if configuration is not ready yet.
            TelegramReportScheduler.scheduleNext(applicationContext)
            return Result.success()
        }

        return try {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val dao = TerminDatabase.getDatabase(applicationContext).terminDao()

                val futureTermine = dao.getAll()
                    .filter { termin ->
                        val scheduledAt = termin.scheduledAtMillis.takeIf { it > 0 }
                            ?: DateTimeUtils.toEpochMillis(termin.date, termin.time)
                            ?: 0L
                        scheduledAt > now
                    }
                    .sortedBy { termin ->
                        termin.scheduledAtMillis.takeIf { it > 0 }
                            ?: DateTimeUtils.toEpochMillis(termin.date, termin.time)
                            ?: Long.MAX_VALUE
                    }

                val messages = createMessages(futureTermine)
                for (chatId in AppConfig.TELEGRAM_RECIPIENT_IDS.distinct()) {
                    for (message in messages) {
                        sendMessage(chatId, message)
                    }
                }
            }

            // A manual send must not enqueue another daily report.
            if (!manualSend) {
                TelegramReportScheduler.scheduleNext(applicationContext)
            }
            Result.success(workDataOf("sent" to true))
        } catch (e: Exception) {
            if (manualSend) {
                Result.failure(
                    workDataOf(
                        "error" to (e.message ?: "Не удалось отправить сообщение в Telegram.")
                    )
                )
            } else {
                Result.retry()
            }
        }
    }

    private fun createMessages(termine: List<TerminEntity>): List<String> {
        val header = "📅 <b>MeineTermine — будущие термины</b>\n"
        if (termine.isEmpty()) {
            return listOf(header + "\nНа данный момент будущих терминов нет.")
        }

        val rows = termine.map { termin ->
            val description = termin.description
                .replace("\n", " ")
                .replace("\r", " ")
                .take(500)
            "• ${escapeHtml(termin.date)}  ${escapeHtml(termin.time)}\n  ${escapeHtml(description)}"
        }

        // Telegram message limit is 4096 characters; keep a safety margin.
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (row in rows) {
            if (current.isNotEmpty() && current.length + row.length + 2 > 3200) {
                chunks += current.toString()
                current = StringBuilder()
            }
            current.append(row).append("\n\n")
        }
        if (current.isNotEmpty()) chunks += current.toString()

        return chunks.mapIndexed { index, chunk ->
            val part = if (chunks.size > 1) " <i>(${index + 1}/${chunks.size})</i>" else ""
            header + part + "\n<pre>" + chunk.trimEnd() + "</pre>"
        }
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun sendMessage(chatId: Long, text: String) {
        val endpoint = URL(
            "https://api.telegram.org/bot${AppConfig.TELEGRAM_BOT_TOKEN}/sendMessage"
        )
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            val payload = JSONObject()
                .put("chat_id", chatId)
                .put("text", text)
                .put("parse_mode", "HTML")
                .put("disable_web_page_preview", true)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val apiOk = runCatching { JSONObject(response).optBoolean("ok", false) }
                .getOrDefault(false)

            if (status !in 200..299 || !apiOk) {
                val apiDescription = runCatching {
                    JSONObject(response).optString("description")
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    if (apiDescription.isNotBlank()) {
                        "Telegram API: $apiDescription"
                    } else {
                        "Telegram API error: HTTP $status"
                    }
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
