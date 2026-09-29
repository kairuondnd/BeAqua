package com.example.beaqua

data class Feedback(
    var id: String = "",
    var orderId: String = "",
    var orderIds: List<String> = emptyList(),
    var customerUsername: String = "",
    var stationOwnerUsername: String = "",
    var stationRating: Float = 0f,
    var productQualityRating: Float = 0f,
    var serviceQualityRating: Float = 0f,
    var productName: String = "",
    var remarks: String = "",
    var timestamp: Long = 0
)
