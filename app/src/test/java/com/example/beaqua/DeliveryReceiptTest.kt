package com.example.beaqua

import org.junit.Assert.*
import org.junit.Test

class DeliveryReceiptTest {
    @Test fun validatesAndCopiesConfirmationWithoutChangingOrder() {
        val order = Order(productName = "Original item", quantity = 2, totalPrice = 55.0)
        val draft = DeliveryReceiptItem("  Confirmed item  ", 3)
        val confirmed = validateReceiptItems(listOf(draft))
        assertEquals("Confirmed item", confirmed.single().name)
        assertEquals(3, confirmed.single().quantity)
        draft.name = "Changed later"
        assertEquals("Confirmed item", confirmed.single().name)
        assertEquals("Original item", order.productName)
        assertEquals(2, order.quantity)
        assertEquals(55.0, order.totalPrice, 0.001)
    }

    @Test fun rejectsEmptyNamesInvalidQuantitiesAndEmptyReceipts() {
        for (items in listOf(emptyList(), listOf(DeliveryReceiptItem(" ", 1)),
            listOf(DeliveryReceiptItem("Water", 0)), listOf(DeliveryReceiptItem("Water", -1)))) {
            try {
                validateReceiptItems(items)
                fail("Invalid receipt should be rejected")
            } catch (_: IllegalArgumentException) { /* Expected. */ }
        }
    }
}
