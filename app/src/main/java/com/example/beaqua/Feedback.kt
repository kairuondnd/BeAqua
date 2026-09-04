package com.example.beaqua

data class Feedback(
    var id: String = "",
    var orderId: String = "",
    var customerUsername: String = "",
    var stationOwnerUsername: String = "",
    var stationRating: Float = 0f,
    var remarks: String = "",
    var timestamp: Long = 0
)
