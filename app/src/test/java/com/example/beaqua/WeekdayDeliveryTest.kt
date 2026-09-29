package com.example.beaqua

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeekdayDeliveryTest {
    private val hours = OperatingHours(openTime = "08:00", closeTime = "18:00", timeZoneId = "Asia/Manila")
    private val mwf = listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
    private fun date(day: Int, hour: Int = 6): Long = Calendar.getInstance(TimeZone.getTimeZone(hours.timeZoneId)).apply {
        clear()
        set(2026, Calendar.SEPTEMBER, day, hour, 0)
    }.timeInMillis

    @Test fun firstDeliveryUsesNextSelectedDayAndDoesNotDuplicateTodaysCheckout() {
        // September 28, 2026 is Monday; next M/W/F delivery is Wednesday, September 30.
        assertEquals(date(30), DeliveryFinalization.firstDeliveryAfter(date(28, 7), 7, hours, mwf))
        assertEquals(date(30), DeliveryFinalization.firstDeliveryAfter(date(29, 18), 7, hours, mwf))
    }

    @Test fun advancesAcrossSelectedDaysAndMonthBoundary() {
        assertEquals(date(30), DeliveryFinalization.nextDelivery(date(28), 7, hours, date(28, 8), mwf))
        assertEquals(date(32), DeliveryFinalization.nextDelivery(date(30), 7, hours, date(30, 8), mwf))
        assertEquals(date(35), DeliveryFinalization.nextDelivery(date(32), 7, hours, date(32, 8), mwf))
    }

    @Test fun singleSundayRepeatsWeeklyAndSaturdayIsSupported() {
        assertEquals(date(34), DeliveryFinalization.firstDeliveryAfter(date(27, 9), 7, hours, listOf(Calendar.SUNDAY)))
        assertEquals(date(33), DeliveryFinalization.firstDeliveryAfter(date(28), 7, hours, listOf(Calendar.SATURDAY)))
    }

    @Test fun missedRunsSkipPastCutoffsButRetainTodaysUnfinalizedDelivery() {
        assertEquals(date(30), DeliveryFinalization.nextDelivery(date(28), 7, hours, date(30, 7), mwf))
        assertEquals(date(32), DeliveryFinalization.nextDelivery(date(28), 7, hours, date(30, 8), mwf))
    }

    @Test fun selectedDeliveryStillQueuesAndRemindsThePreviousDay() {
        val delivery = DeliveryFinalization.firstDeliveryAfter(date(28), 7, hours, mwf)
        assertEquals(date(29, 0), DeliveryFinalization.queueAt(delivery, hours))
        assertEquals(date(29, 9), DeliveryFinalization.reminderAt(delivery, hours))
        assertTrue(DeliveryFinalization.canEdit(delivery, hours, date(30, 7)))
        assertFalse(DeliveryFinalization.canEdit(delivery, hours, date(30, 8)))
    }

    @Test fun allDaysBehavesAsDailyAndUsesStationTimezone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(date(29), DeliveryFinalization.firstDeliveryAfter(date(28, 23), 7, hours, (1..7).toList()))
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun labelsAndLegacyIntervalsRemainCorrect() {
        assertEquals("Every Monday, Wednesday, Friday", WeeklySubscription(deliveryWeekdays = mwf.reversed()).scheduleLabel())
        assertEquals("Every 3 day(s)", WeeklySubscription(repeatEveryDays = 3).scheduleLabel())
        assertEquals(date(31), DeliveryFinalization.firstDeliveryAfter(date(28), 3, hours))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWeekdayIsRejected() {
        DeliveryFinalization.firstDeliveryAfter(date(28), 7, hours, listOf(8))
    }
}
