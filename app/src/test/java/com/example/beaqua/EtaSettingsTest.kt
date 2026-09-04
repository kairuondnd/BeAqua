package com.example.beaqua

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class EtaSettingsTest {

    private val settings = EtaSettings(
        window1Start = "07:00",
        window1End = "11:00",
        window1Eta = "1:00 PM - 4:00 PM",
        window2Start = "12:00",
        window2End = "15:00",
        window2Eta = "4:00 PM - 6:00 PM",
        defaultEta = "1:00 PM - 4:00 PM (Next Day)"
    )

    @Test
    fun firstOrderBatchReturnsFirstDeliveryEstimate() {
        assertEquals("1:00 PM - 4:00 PM", settings.deliveryEstimateAt(atTime(9, 30)))
    }

    @Test
    fun secondOrderBatchReturnsSecondDeliveryEstimate() {
        assertEquals("4:00 PM - 6:00 PM", settings.deliveryEstimateAt(atTime(13, 15)))
    }

    @Test
    fun timeOutsideBothBatchesReturnsFallbackEstimate() {
        assertEquals(
            "1:00 PM - 4:00 PM (Next Day)",
            settings.deliveryEstimateAt(atTime(11, 30))
        )
        assertEquals(
            "1:00 PM - 4:00 PM (Next Day)",
            settings.deliveryEstimateAt(atTime(16, 0))
        )
    }

    @Test
    fun weeklyDeliveryChoicesExcludeNextDayFallback() {
        assertEquals(
            listOf("1:00 PM - 4:00 PM", "4:00 PM - 6:00 PM"),
            settings.customerDeliveryWindows()
        )
    }

    private fun atTime(hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
