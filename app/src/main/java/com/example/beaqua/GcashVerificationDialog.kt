package com.example.beaqua

import android.content.Context
import android.widget.*
import androidx.appcompat.app.AlertDialog

object GcashVerificationDialog {
    fun show(context: Context, group: OrderGroup, onConfirmed: () -> Unit, onRejected: () -> Unit) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        layout.addView(TextView(context).apply {
            text = "Amount: ₱${String.format("%,.2f", group.totalPrice)}\nCheck the receipt against the payment received in your own GCash account, including amount, recipient, date and reference number. A screenshot alone does not confirm payment."
        })
        val urls = group.items.map { it.gcashReceiptUrl }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty()) layout.addView(TextView(context).apply {
            text = "No receipt attached to this older order. Contact the customer through chat before confirming payment."
        })
        urls.forEach { url ->
            layout.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, (440 * resources.displayMetrics.density).toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Customer GCash payment receipt"
                ContainerImageLoader.load(this, url)
                setOnClickListener {
                    val image = ImageView(context).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        ContainerImageLoader.load(this, url)
                    }
                    AlertDialog.Builder(context).setTitle("Payment receipt")
                        .setView(ScrollView(context).apply { addView(image) }).setPositiveButton("Close", null).show()
                }
            })
        }
        AlertDialog.Builder(context).setTitle("Verify GCash payment")
            .setView(ScrollView(context).apply { addView(layout) })
            .setPositiveButton("Confirm payment") { _, _ -> onConfirmed() }
            .setNeutralButton("Reject with reason") { _, _ -> onRejected() }
            .setNegativeButton("Cancel", null).show()
    }

    fun reject(context: Context, group: OrderGroup, stationUsername: String, onSuccess: () -> Unit) {
        val reason = EditText(context).apply {
            hint = "Why is this order being rejected?"
            minLines = 2
            filters = arrayOf(android.text.InputFilter.LengthFilter(500))
        }
        val dialog = AlertDialog.Builder(context).setTitle("Reject order")
            .setMessage("The customer will receive your reason and a prompt to contact you through chat about any order or payment concerns.")
            .setView(reason).setPositiveButton("Reject order", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = reason.text.toString().trim()
                if (text.isEmpty()) { reason.error = "Enter a rejection reason."; return@setOnClickListener }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                FirebaseHelper.rejectCheckout(group.ids, stationUsername, text)
                    .addOnSuccessListener { dialog.dismiss(); onSuccess() }
                    .addOnFailureListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        reason.error = "Could not reject order: ${it.message}"
                    }
            }
        }
        dialog.show()
    }
}
