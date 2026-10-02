package com.example.beaqua

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object OrderLocation {
    fun label(order: Order): String = "Location: ${order.customerAddress.ifBlank { "Address not provided" }}"

    fun hasPin(order: Order): Boolean {
        val lat = order.customerLat ?: return false
        val lon = order.customerLon ?: return false
        return lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)
    }

    fun open(context: Context, order: Order) {
        if (!hasPin(order)) {
            Toast.makeText(context, "No delivery pin saved for this order. Contact the customer through chat.", Toast.LENGTH_LONG).show()
            return
        }
        context.startActivity(Intent(context, OrderLocationActivity::class.java)
            .putExtra("LATITUDE", order.customerLat!!)
            .putExtra("LONGITUDE", order.customerLon!!)
            .putExtra("CUSTOMER", order.customerName)
            .putExtra("ADDRESS", order.customerAddress))
    }
}
