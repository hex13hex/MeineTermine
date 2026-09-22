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
}
