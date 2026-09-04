package com.example.beaqua

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Product(
    var id: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var imageUri: String? = null,
    var ownerUsername: String = ""
)
