package com.example.beaqua

data class CartItem(
    var id: String = "",
    var productId: String = "",
    var productName: String = "",
    var productPrice: Double = 0.0,
    var quantity: Int = 0,
    var containerType: String = "",
    var deliveryTimeSlot: String = "",
    var paymentMethod: String = "",
    var stationOwnerUsername: String = "",
    var stationName: String = "",
    var customerUsername: String = "",
    var imageUri: String? = null,
    var totalPrice: Double = 0.0,
    var offeringType: String = OFFERING_PURCHASE,
    var refillServiceId: String = "",
    var refillInstructions: String = "",
    var emptyContainerCount: Int = 0
) {
    init {
        if (totalPrice == 0.0 && productPrice > 0 && quantity > 0) {
            totalPrice = productPrice * quantity
        }
    }
}
