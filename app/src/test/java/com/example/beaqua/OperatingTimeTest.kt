package com.example.beaqua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperatingTimeTest {
    @Test fun midnightAndNoonKeepTheirMeaning() {
        assertEquals("12:00AM", "00:00".toDisplayTime())
        assertEquals("12:00PM", "12:00".toDisplayTime())
        assertEquals("00:00", normalizeOperatingTime("12:00AM"))
        assertEquals("12:00", normalizeOperatingTime("12:00PM"))
    }

    @Test fun displayAndStorageRoundTripEveryMinute() {
        for (hour in 0..23) for (minute in 0..59) {
            val stored = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
            assertEquals(stored, normalizeOperatingTime(stored.toDisplayTime()))
        }
        assertEquals("8:00AM", "08:00".toDisplayTime())
        assertEquals("8:00PM", "20:00".toDisplayTime())
        assertEquals("20:15", normalizeOperatingTime("8:15 pm"))
    }

    @Test fun rejectsInvalidTimes() {
        listOf("00:00AM", "13:00PM", "8:60AM", "24:00", "8:00XM", "").forEach {
            assertNull(it, normalizeOperatingTime(it))
        }
    }
}
