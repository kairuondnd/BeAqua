package com.example.beaqua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DeliveryEtaTest {
    private val timeZoneId = "Asia/Manila"

    @Test
    fun labelsTodayTomorrowAndLaterDatesWithCalendarDate() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val now = DeliveryEta.fromDate(2026, 8, 20, timeZoneId)

            assertEquals("Today - Sep 20, 2026", DeliveryEta.label(now, timeZoneId, now))
            assertEquals(
                "Tomorrow - Sep 21, 2026",
                DeliveryEta.label(DeliveryEta.tomorrow(now, timeZoneId), timeZoneId, now)
            )
            assertEquals(
                "Sep 25, 2026",
                DeliveryEta.label(DeliveryEta.fromDate(2026, 8, 25, timeZoneId), timeZoneId, now)
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun onlyTodayOrFutureDatesCanBeUsedAsEta() {
        val now = DeliveryEta.fromDate(2026, 8, 20, timeZoneId)

        assertFalse(
            DeliveryEta.isTodayOrFuture(
                DeliveryEta.fromDate(2026, 8, 19, timeZoneId),
                now,
                timeZoneId
            )
        )
        assertTrue(DeliveryEta.isTodayOrFuture(now, now, timeZoneId))
        assertTrue(
            DeliveryEta.isTodayOrFuture(
                DeliveryEta.fromDate(2026, 8, 21, timeZoneId),
                now,
                timeZoneId
            )
        )
    }
}
