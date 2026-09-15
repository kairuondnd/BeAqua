package com.example.beaqua

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DeliveryRecurrenceTest {
    private fun date(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 6, 0)
    }.timeInMillis

    @Test fun eightDayIntervalCrossesMonthBoundary() {
        val start = date(2026, 9, 28)
        assertEquals(date(2026, 10, 6), DeliveryRecurrence.nextAfter(start, 8, start))
    }

    @Test fun missedDatesAdvanceWithoutChangingCadence() {
        assertEquals(date(2026, 9, 28), DeliveryRecurrence.nextAfter(
            date(2026, 9, 1), 9, date(2026, 9, 20)))
    }

    @Test fun futureDateIsPreserved() {
        assertEquals(date(2026, 9, 20), DeliveryRecurrence.nextAfter(
            date(2026, 9, 20), 8, date(2026, 9, 16)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroIntervalRejected() {
        DeliveryRecurrence.nextAfter(date(2026, 9, 1), 0, date(2026, 9, 2))
    }

    @Test fun existingSchedulesDefaultToSevenDays() {
        assertEquals(7, WeeklySubscription().repeatEveryDays)
    }
}
