package com.example.beaqua

data class DeliveryReceiptItem(var name: String = "", var quantity: Int = 0)

data class DeliveryReceipt(
    var reference: String = "",
    var issuedAt: Long = 0L,
    var issuedBy: String = "",
    var items: List<DeliveryReceiptItem> = emptyList(),
    var itemsChanged: Boolean = false
)

fun validateReceiptItems(items: List<DeliveryReceiptItem>): List<DeliveryReceiptItem> {
    require(items.isNotEmpty()) { "The receipt must contain at least one item." }
    return items.map {
        require(it.name.isNotBlank()) { "Enter a name for every item." }
        require(it.quantity > 0) { "Enter a positive whole-number quantity for every item." }
        it.copy(name = it.name.trim())
    }
}

/** Compare totals by item name so sorting or combining identical rows is not a change. */
fun receiptItemsChanged(orders: List<Order>, items: List<DeliveryReceiptItem>): Boolean {
    fun totals(values: List<DeliveryReceiptItem>) = values
        .groupBy { it.name.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT) }
        .mapValues { (_, lines) -> lines.sumOf { it.quantity.toLong() } }
    return totals(orders.map { DeliveryReceiptItem(it.productName, it.quantity) }) != totals(items)
}

fun deliveryReceiptNotification(orders: List<Order>, receipt: DeliveryReceipt): BeAquaNotification {
    val order = orders.first()
    val station = order.stationName.ifBlank { order.stationOwnerUsername }
    return BeAquaNotification(
        id = "delivery-${receipt.reference}", recipientUsername = order.customerName,
        title = if (receipt.itemsChanged) "Order delivered — receipt updated" else "Order delivered",
        message = if (receipt.itemsChanged) {
            "$station updated the items or quantities in your receipt for order ${receipt.reference}. Tap to view your receipt."
        } else {
            "Your order ${receipt.reference} from $station has been delivered. Tap to view your receipt."
        },
        type = if (receipt.itemsChanged) BeAquaNotification.TYPE_RECEIPT_UPDATED else BeAquaNotification.TYPE_ORDER_DELIVERED,
        orderId = order.id, createdAt = receipt.issuedAt
    )
}
