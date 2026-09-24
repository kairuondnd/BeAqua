package com.example.beaqua

import org.junit.Assert.assertEquals
import org.junit.Test

class SalesGraphTest {

    @Test
    fun salesPointCreation_holdsRevenueAndOrderCount() {
        val point = SalesPoint(revenue = 150.0, orderCount = 3, label = "Today")
        assertEquals(150.0, point.revenue, 0.001)
        assertEquals(3, point.orderCount)
        assertEquals("Today", point.label)
    }

    @Test
    fun salesGraphView_setSales_populatesSalesPoints() {
        val values = listOf(50.0, 100.0, 200.0)
        val points = values.map { SalesPoint(revenue = it, orderCount = 1, label = "Point") }
        assertEquals(3, points.size)
        assertEquals(50.0, points[0].revenue, 0.001)
        assertEquals(100.0, points[1].revenue, 0.001)
        assertEquals(200.0, points[2].revenue, 0.001)
    }
}
