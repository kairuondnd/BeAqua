package com.example.beaqua

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class StationPremiumMembership(
    var customerUsername: String = "",
    var stationOwnerUsername: String = "",
    var stationName: String = "",
    var pricePaid: Double = 0.0,
    var purchasedAt: Long = 0L,
    var expiresAt: Long = 0L,
    var paymentReference: String = "",
    var pendingPaymentReference: String = "",
    var pendingPrice: Double = 0.0,
    var paymentStatus: String = "",
    var paymentRequestedAt: Long = 0L,
    var cancelAtPeriodEnd: Boolean = false,
    var cancellationRequestedAt: Long = 0L
) {
    fun isActiveFor(
        customer: String,
        stationOwner: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        return customerUsername == customer &&
            stationOwnerUsername == stationOwner &&
            expiresAt > now
    }
}
