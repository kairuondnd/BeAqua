package com.example.beaqua

fun Order.recurringDeliveryLabel(now: Long = System.currentTimeMillis()): String =
    if (isSubscriptionOrder && scheduledDeliveryDate > 0L)
        "Deliver: ${DeliveryEta.label(scheduledDeliveryDate, scheduledDeliveryTimeZoneId, now)}"
    else ""

fun Order.recurringDeliveryNotice(now: Long = System.currentTimeMillis()): String {
    val label = recurringDeliveryLabel(now)
    if (label.isEmpty()) return ""
    val early = DeliveryEta.normalize(scheduledDeliveryDate, scheduledDeliveryTimeZoneId) >
        DeliveryEta.today(now, scheduledDeliveryTimeZoneId)
    return label + if (early)
        "\nContact the customer through chat before delivering today. Items may change until the station opens on delivery day."
    else ""
}
