package com.example.beaqua

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DeliveryFinalizationTest {
    @Test fun september23EntersQueueAtMidnightSeptember22AndStaysEditable() {
        val delivery = date(23, 6)
        assertEquals(date(22, 0), DeliveryFinalization.queueAt(delivery, hours))
        assertEquals(DeliveryFinalization.QueueAction.WAIT,
            DeliveryFinalization.queueAction(delivery, hours, date(21, 23, 59), false))
        assertEquals(DeliveryFinalization.QueueAction.CREATE,
            DeliveryFinalization.queueAction(delivery, hours, date(22, 0), false))
        assertTrue(DeliveryFinalization.canEdit(delivery, hours, date(22, 12)))
        assertEquals(DeliveryFinalization.QueueAction.WAIT,
            DeliveryFinalization.queueAction(delivery, hours, date(23, 7, 59), true))
        assertEquals(DeliveryFinalization.QueueAction.ADVANCE,
            DeliveryFinalization.queueAction(delivery, hours, date(23, 8), true))
    }

    @Test fun everyIntervalQueuesExactlyOneCalendarDayEarly() {
        for (days in listOf(1, 3, 5, 6, 7, 30)) {
            val delivery = DeliveryFinalization.firstDeliveryAfter(date(1, 12), days, hours)
            assertEquals(date(days, 0), DeliveryFinalization.queueAt(delivery, hours))
        }
    }

    @Test fun dailyScheduleCanQueueTomorrowAfterTodaysCutoff() {
        val now = date(23, 8)
        val next = DeliveryFinalization.nextDelivery(date(23, 6), 1, hours, now)
        assertEquals(date(24, 6), next)
        assertEquals(DeliveryFinalization.QueueAction.CREATE,
            DeliveryFinalization.queueAction(next, hours, now, false))
        assertEquals(DeliveryFinalization.QueueAction.WAIT,
            DeliveryFinalization.queueAction(next, hours, now, true))
    }

    @Test fun queueDateCrossesMonthsAndUsesStationTimezone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(date(30, 0), DeliveryFinalization.queueAt(date(31, 6), hours))
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun noticeChangesFromTomorrowToTodayAndOrdinaryOrdersHaveNoNotice() {
        val order = Order(isSubscriptionOrder = true, scheduledDeliveryDate = date(23, 6))
        assertTrue(order.recurringDeliveryNotice(date(22, 12)).contains("Tomorrow"))
        assertTrue(order.recurringDeliveryNotice(date(22, 12)).contains("Sep 23, 2026"))
        assertTrue(order.recurringDeliveryNotice(date(22, 12)).contains("chat before delivering today"))
        assertTrue(order.recurringDeliveryNotice(date(23, 9)).contains("Today"))
        assertFalse(order.recurringDeliveryNotice(date(23, 9)).contains("Tomorrow"))
        assertEquals("", Order().recurringDeliveryNotice(date(22, 12)))
    }

    private val hours = OperatingHours(openTime = "08:00", closeTime = "18:00")
    private fun date(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, day, hour, minute)
        }.timeInMillis

    @Test fun threeDayDeliveryRemindsOnDayTwo() {
        val delivery = date(3, 6)
        assertFalse(DeliveryFinalization.shouldRemind(delivery, hours, date(1, 12)))
        assertFalse(DeliveryFinalization.shouldRemind(delivery, hours, date(2, 8, 59)))
        assertTrue(DeliveryFinalization.shouldRemind(delivery, hours, date(2, 9)))
        assertTrue(DeliveryFinalization.shouldRemind(delivery, hours, date(3, 7, 59)))
        assertFalse(DeliveryFinalization.shouldRemind(delivery, hours, date(3, 8)))
    }

    @Test fun savedSixAmTimestampDoesNotCloseEditingBeforeEightAm() {
        assertTrue(DeliveryFinalization.canEdit(date(3, 6), hours, date(3, 7, 59)))
        assertFalse(DeliveryFinalization.canEdit(date(3, 6), hours, date(3, 8)))
        assertFalse(DeliveryFinalization.canEdit(date(3, 6), hours, date(3, 9)))
    }

    @Test fun stationHoursDetermineCutoff() {
        assertEquals(date(3, 10, 30), DeliveryFinalization.cutoff(date(3, 6), hours.copy(openTime = "10:30")))
        assertEquals(date(3, 5), DeliveryFinalization.cutoff(date(3, 6), hours.copy(openTime = "05:00")))
        assertEquals(date(3, 0), DeliveryFinalization.cutoff(date(3, 6), OperatingHours()))
    }

    @Test fun threeDayCadenceAdvancesAfterFinalization() {
        assertEquals(date(6, 6), DeliveryFinalization.nextDelivery(date(3, 6), 3, hours, date(3, 8)))
    }

    @Test fun firstDeliveryIsIntervalDaysFromToday() {
        assertEquals(date(8, 6), DeliveryFinalization.firstDeliveryAfter(date(3, 15), 5, hours))
    }

    @Test fun delayedProcessingKeepsTodayIfOpeningHasNotPassed() {
        assertEquals(date(6, 6), DeliveryFinalization.nextDelivery(date(3, 6), 3, hours, date(6, 7)))
        assertEquals(date(9, 6), DeliveryFinalization.nextDelivery(date(3, 6), 3, hours, date(6, 8)))
    }

    @Test fun reminderCrossesMonthBoundary() {
        assertEquals(date(30, 9), DeliveryFinalization.reminderAt(date(31, 6), hours))
    }

    @Test fun deviceTimezoneDoesNotChangeStationCutoff() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(date(3, 8), DeliveryFinalization.cutoff(date(3, 6), hours))
        } finally { TimeZone.setDefault(previous) }
    }
}
