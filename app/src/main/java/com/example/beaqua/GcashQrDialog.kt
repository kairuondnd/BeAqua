package com.example.beaqua

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Locale

object GcashQrDialog {
    fun show(context: Context, station: User, amount: Double,
             submitLabel: String = "I've paid — submit order", onSubmitted: () -> Unit) {
        if (station.gcashQrUrl.isBlank()) {
            Toast.makeText(context, "This station has not uploaded a GCash QR code. Please choose another payment method.", Toast.LENGTH_LONG).show()
            return
        }
        val density = context.resources.displayMetrics.density
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), (16 * density).toInt())
        }
        layout.addView(TextView(context).apply {
            text = "Pay ${station.name.ifBlank { station.username }}\n" +
                String.format(Locale.getDefault(), "Amount: ₱%,.2f\n\n", amount) +
                "Scan this QR using GCash. Check the recipient and amount before paying. " +
                "You can screenshot this code and import it in GCash.\n\n" +
                "The station will verify your payment. Submitting does not confirm payment."
        })
        layout.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (300 * density).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "${station.name} GCash payment QR code"
            setBackgroundColor(android.graphics.Color.WHITE)
            ContainerImageLoader.load(this, station.gcashQrUrl)
        })
        AlertDialog.Builder(context).setTitle("Pay with GCash")
            .setView(ScrollView(context).apply { addView(layout) })
            .setPositiveButton(submitLabel) { _, _ -> onSubmitted() }
            .setNegativeButton("Cancel", null).show()
    }
}
