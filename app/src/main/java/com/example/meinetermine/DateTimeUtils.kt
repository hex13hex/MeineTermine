package com.example.meinetermine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DateTimeUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun toEpochMillis(date: String, time: String): Long? = runCatching {
        LocalDateTime.of(
            LocalDate.parse(date, dateFormatter),
            LocalTime.parse(time, timeFormatter)
        ).atZone(AppConfig.GERMANY_ZONE_ID).toInstant().toEpochMilli()
    }.getOrNull()

    fun isPast(date: String, time: String): Boolean =
        (toEpochMillis(date, time) ?: 0L) <= System.currentTimeMillis()

    /**
     * Returns occurrences on the weekday of [firstDate] for the next two
     * calendar months. The selected date is the first occurrence.
     */
    fun weeklyOccurrences(firstDate: String): List<String> = runCatching {
        val start = LocalDate.parse(firstDate, dateFormatter)
        val endExclusive = start.plusMonths(2)
        generateSequence(start) { it.plusWeeks(1) }
            .takeWhile { it.isBefore(endExclusive) }
            .map { it.format(dateFormatter) }
            .toList()
    }.getOrDefault(emptyList())

    fun section(date: String): String {
        val appointmentDate = runCatching { LocalDate.parse(date, dateFormatter) }.getOrNull()
            ?: return "Other"
        val today = LocalDate.now(AppConfig.GERMANY_ZONE_ID)
        return when (appointmentDate) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.plusDays(2) -> "DayAfterTomorrow"
            else -> "Other"
        }
    }

    fun displayDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(AppConfig.GERMANY_ZONE_ID).toLocalDate().format(dateFormatter)
}
