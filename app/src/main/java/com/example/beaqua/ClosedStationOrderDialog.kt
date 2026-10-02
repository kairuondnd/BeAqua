package com.example.beaqua

import android.content.Context
import androidx.appcompat.app.AlertDialog

object ClosedStationOrderDialog {
    fun confirm(context: Context, station: User, onConfirmed: () -> Unit) {
        if (station.isStationOpen()) {
            onConfirmed()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Water station is currently closed")
            .setMessage("${station.name.ifBlank { "This water station" }} is currently closed. Would you like to place an order for later?\n\nYour order will be sent to the station's pending queue immediately after checkout. The station will confirm the delivery date when accepting it.\n\n${station.operatingHoursLabel()}")
            .setPositiveButton("Order for later") { _, _ -> onConfirmed() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
