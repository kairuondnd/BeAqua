package com.example.beaqua

import org.junit.Assert.*
import org.junit.Test

class DeliveryReceiptNotificationTest {
    private val orders = listOf(
        Order(id = "ORDER-1", checkoutId = "CHECKOUT", customerName = "customer", stationName = "Water Station", productName = "Purified water", quantity = 2),
        Order(id = "ORDER-2", checkoutId = "CHECKOUT", customerName = "customer", stationName = "Water Station", productName = "Mineral water", quantity = 1)
    )

    @Test fun unchangedItemsIgnoreOrderingAndWhitespace() {
        assertFalse(receiptItemsChanged(orders, listOf(
            DeliveryReceiptItem(" Mineral water ", 1), DeliveryReceiptItem("PURIFIED   WATER", 2)
        )))
        assertFalse(receiptItemsChanged(orders + orders.first(), listOf(
            DeliveryReceiptItem("Purified water", 4), DeliveryReceiptItem("Mineral water", 1)
        )))
    }

    @Test fun detectsQuantityChangesAndReplacementsAndRemovedItems() {
        assertTrue(receiptItemsChanged(orders, listOf(DeliveryReceiptItem("Purified water", 3), DeliveryReceiptItem("Mineral water", 1))))
        assertTrue(receiptItemsChanged(orders, listOf(DeliveryReceiptItem("New water", 2), DeliveryReceiptItem("Mineral water", 1))))
        assertTrue(receiptItemsChanged(orders, listOf(DeliveryReceiptItem("Purified water", 2))))
    }

    @Test fun oneNotificationTargetsTheReceiptForEachCompletion() {
        val receipt = DeliveryReceipt(reference = "ORDER-1", issuedAt = 100L)
        val normal = deliveryReceiptNotification(orders, receipt)
        val changed = deliveryReceiptNotification(orders, receipt.copy(itemsChanged = true))
        assertEquals(BeAquaNotification.TYPE_ORDER_DELIVERED, normal.type)
        assertEquals(BeAquaNotification.TYPE_RECEIPT_UPDATED, changed.type)
        assertFalse(normal.message.contains("updated"))
        assertTrue(changed.message.contains("items or quantities"))
        assertTrue(normal.message.contains("Tap to view your receipt"))
        assertTrue(changed.message.contains("Tap to view your receipt"))
        assertEquals("customer", changed.recipientUsername)
        assertEquals("ORDER-1", changed.orderId)
        assertEquals(normal.id, changed.id)
        assertEquals(normal.id, deliveryReceiptNotification(orders, receipt).id)
        assertNotEquals(normal.id, deliveryReceiptNotification(orders, receipt.copy(reference = "ORDER-3")).id)
    }
}
