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

    @Test fun legacySingleItemScheduleIsReadAsOneDeliveryItem() {
        val delivery = WeeklySubscription(
            productId = "bottle-1",
            productName = "Water 1",
            quantity = 3,
            containerType = "Water 1"
        )

        assertEquals(listOf(RecurringDeliveryItem("bottle-1", "Water 1", 3, "Water 1")), delivery.deliveryItems())
    }

    @Test fun multiItemScheduleUsesSavedItemQuantities() {
        val delivery = WeeklySubscription(
            productId = "legacy",
            quantity = 99,
            items = listOf(
                RecurringDeliveryItem("a", "Small", 2, "Small"),
                RecurringDeliveryItem("b", "Large", 4, "Large")
            )
        )

        assertEquals(listOf(2, 4), delivery.deliveryItems().map { it.quantity })
    }
}
