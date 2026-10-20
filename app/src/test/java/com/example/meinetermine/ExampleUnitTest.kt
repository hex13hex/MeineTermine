package com.example.meinetermine

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun weeklyOccurrences_keepsWeekdayAndStopsBeforeTwoMonthBoundary() {
        val occurrences = DateTimeUtils.weeklyOccurrences("05.01.2026")

        assertEquals(
            listOf(
                "05.01.2026", "12.01.2026", "19.01.2026", "26.01.2026",
                "02.02.2026", "09.02.2026", "16.02.2026", "23.02.2026"
            ),
            occurrences
        )
    }

    @Test
    fun parseCsv_readsAutomaticDriveBackupAndEscapedDescription() {
        val backup = BackupManager.parseCsv(
            "\"id\",\"date\",\"time\",\"scheduled_at_millis\",\"description\"\r\n" +
                    "\"42\",\"20.10.2026\",\"15:00\",\"1792501200000\",\"Врач, \"\"кабинет 3\"\"\"\r\n"
        )

        assertEquals(1, backup.size)
        assertEquals(42L, backup.single().id)
        assertEquals("Врач, \"кабинет 3\"", backup.single().description)
    }
}
