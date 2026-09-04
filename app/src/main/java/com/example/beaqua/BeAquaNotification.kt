package com.example.beaqua

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BeAquaNotification(
    var id: String = "",
    var recipientUsername: String = "",
    var title: String = "",
    var message: String = "",
    var type: String = "",
    var orderId: String = "",
    var createdAt: Long = 0L,
    var deliveredAt: Long = 0L
) {
    companion object {
        const val TYPE_ORDER_AUTO_CANCELLED = "ORDER_AUTO_CANCELLED"
    }
}
