package com.example.beaqua

/** Item documents remain separate for inventory, revenue and older app versions. */
data class OrderGroup(val items: List<Order>) {
    init { require(items.isNotEmpty()) }

    val first: Order get() = items.first()
    val ids: List<String> get() = items.map { it.id }
    val totalPrice: Double get() = items.sumOf { it.totalPrice }
    val isPaid: Boolean get() = items.all { it.isPaid }

    fun itemSummary(): String = items.joinToString("\n") { item ->
        buildString {
            append("${item.productName} (Qty: ${item.quantity})")
            if (item.isRefill()) {
                append("\nRefill exchange: ${item.emptyContainerCount.coerceAtLeast(item.quantity)} empty ${item.containerType} container(s)")
                if (item.refillInstructions.isNotBlank()) append("\nNotes: ${item.refillInstructions}")
            }
        }
    }
}

/** Exact timestamps support old checkouts; never merge merely nearby orders. */
fun groupCheckoutOrders(orders: List<Order>): List<OrderGroup> = orders.withIndex()
    .groupBy { (index, order) ->
        listOf(
            when {
                order.checkoutId.isNotBlank() -> "checkout:${order.checkoutId}"
                order.timestamp > 0 -> "legacy:${order.timestamp}"
                else -> "ungrouped:$index"
            },
            order.customerName, order.stationOwnerUsername, order.customerAddress,
            order.status, order.paymentMethod, order.isPaid.toString(),
            order.isSubscriptionOrder.toString(), order.subscriptionId,
            order.scheduledDeliveryDate.toString(), order.estimatedDeliveryDate.toString(),
            order.estimatedDeliveryTimeZoneId
        )
    }.values.map { entries -> OrderGroup(entries.map { it.value }) }
    .sortedByDescending { it.first.timestamp }
