package com.example.beaqua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderExpiryTest {

    @Test
    fun pendingOrderExpiresAtConfiguredDeadline() {
        val order = Order(
            status = "Pending",
            timestamp = 100L,
            pendingExpiresAt = 1_000L
        )

        assertFalse(order.isExpiredPending(now = 999L))
        assertTrue(order.isExpiredPending(now = 1_000L))
    }

    @Test
    fun legacyPendingOrderUsesTimestampPlus24Hours() {
        val placedAt = 10_000L
        val order = Order(status = "Pending", timestamp = placedAt)

        assertEquals(placedAt + PENDING_ORDER_TIMEOUT_MILLIS, order.pendingDeadline())
        assertTrue(
            order.isExpiredPending(now = placedAt + PENDING_ORDER_TIMEOUT_MILLIS)
        )
    }

    @Test
    fun acceptedOrderNeverExpiresAsPending() {
        val order = Order(
            status = "Accepted",
            timestamp = 100L,
            pendingExpiresAt = 1_000L
        )

        assertFalse(order.isExpiredPending(now = 2_000L))
    }
}
