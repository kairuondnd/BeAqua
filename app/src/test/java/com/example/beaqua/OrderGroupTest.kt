package com.example.beaqua

import org.junit.Assert.*
import org.junit.Test

class OrderGroupTest {
    private val first = Order(
        id = "one", customerName = "user1", stationOwnerUsername = "station1",
        timestamp = 1000L, productName = "Water1", quantity = 2,
        totalPrice = 45.0, deliveryFee = 5.0
    )
    private val second = first.copy(id = "two", productName = "Water2", quantity = 3, totalPrice = 65.0)

    @Test fun legacyCheckoutGroupsAllItemsAndPreservesQuantitiesAndTotals() {
        val group = groupCheckoutOrders(listOf(first, second)).single()
        assertEquals(listOf("one", "two"), group.ids)
        assertEquals(110.0, group.totalPrice, 0.001)
        assertEquals("Water1 (Qty: 2)\nWater2 (Qty: 3)", group.itemSummary())
    }

    @Test fun explicitCheckoutIdGroupsEvenIfItemTimestampsDiffer() {
        assertEquals(1, groupCheckoutOrders(listOf(
            first.copy(checkoutId = "checkout-a"),
            second.copy(checkoutId = "checkout-a", timestamp = 1001L)
        )).size)
    }

    @Test fun separateCheckoutsNeverMergeDespiteIdenticalTimeAndCustomer() {
        assertEquals(2, groupCheckoutOrders(listOf(
            first.copy(checkoutId = "checkout-a"), second.copy(checkoutId = "checkout-b")
        )).size)
        assertEquals(2, groupCheckoutOrders(listOf(first, second.copy(timestamp = 1001L))).size)
    }

    @Test fun differentCustomersStationsAndRecurringDeliveriesStaySeparate() {
        for (other in listOf(
            second.copy(customerName = "user2"),
            second.copy(stationOwnerUsername = "station2"),
            second.copy(isSubscriptionOrder = true, subscriptionId = "recurring")
        )) assertEquals(2, groupCheckoutOrders(listOf(first, other)).size)
    }

    @Test fun previouslySplitStatusesPaymentsAndEtasStayActionableSeparately() {
        for (other in listOf(
            second.copy(status = "Accepted"), second.copy(isPaid = true),
            second.copy(paymentMethod = "GCash"), second.copy(estimatedDeliveryDate = 2000L)
        )) assertEquals(2, groupCheckoutOrders(listOf(first, other)).size)
    }

    @Test fun missingCheckoutIdentityDoesNotMergeUnknownOrders() {
        assertEquals(2, groupCheckoutOrders(listOf(first.copy(timestamp = 0), second.copy(timestamp = 0))).size)
        assertTrue(groupCheckoutOrders(emptyList()).isEmpty())
    }

    @Test fun refillDetailsAndPaymentStateArePreserved() {
        val group = OrderGroup(listOf(first.copy(isPaid = true), second.copy(
            offeringType = OFFERING_REFILL, emptyContainerCount = 3,
            containerType = "jug", refillInstructions = "Bring caps"
        )))
        assertFalse(group.isPaid)
        assertTrue(group.itemSummary().contains("3 empty jug container(s)"))
        assertTrue(group.itemSummary().contains("Notes: Bring caps"))
    }
}
