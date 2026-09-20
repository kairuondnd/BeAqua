package com.example.beaqua

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object RecurringDeliveryEditor {
    private data class ItemControl(
        val product: Product,
        val selected: CheckBox,
        val quantity: EditText
    )

    fun show(
        context: Context,
        customer: User,
        station: User,
        existing: WeeklySubscription? = null,
        preferredProductId: String = "",
        initialQuantity: Int = 1,
        checkoutItem: CartItem? = null,
        inlineContainer: LinearLayout? = null,
        onSavingChanged: (Boolean) -> Unit = {},
        onCancelled: () -> Unit = {},
        onSaved: () -> Unit
    ) {
        if (customer.address.isBlank()) {
            Toast.makeText(context, "Add your delivery address in Profile first", Toast.LENGTH_LONG).show()
            onCancelled()
            return
        }
        if (checkoutItem?.offeringType == OFFERING_REFILL) {
            Toast.makeText(
                context,
                "Recurring delivery is available for station sale items only",
                Toast.LENGTH_LONG
            ).show()
            onCancelled()
            return
        }

        val hours = station.operatingHours
        val stationTimeZone = TimeZone.getTimeZone(hours.timeZoneId)
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
            timeZone = stationTimeZone
        }
        fun cutoffLabel(timestamp: Long) =
            SimpleDateFormat("MMM d, yyyy h:mm a z", Locale.getDefault()).apply {
                timeZone = stationTimeZone
            }.format(Date(DeliveryFinalization.cutoff(timestamp, hours)))

        if (
            existing != null &&
            !DeliveryFinalization.canEdit(existing.nextDeliveryAt, hours, System.currentTimeMillis())
        ) {
            AlertDialog.Builder(context)
                .setTitle("Delivery finalized")
                .setMessage(
                    "Editing closed at ${cutoffLabel(existing.nextDeliveryAt)}, when ${station.name} opens. " +
                        "Refresh to manage your next delivery."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        FirebaseHelper.getProductsByStation(station.username).addOnSuccessListener { result ->
            val products = result.toObjects(Product::class.java)
                .filter { it.ownerUsername == station.username }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }

            if (products.isEmpty() || !station.isApprovedStationOwner()) {
                Toast.makeText(
                    context,
                    "This station has no sale items available for recurring delivery",
                    Toast.LENGTH_LONG
                ).show()
                onCancelled()
                return@addOnSuccessListener
            }

            val savedItems = existing?.deliveryItems().orEmpty().associateBy { it.productId }
            val preferredId = checkoutItem?.productId
                ?: preferredProductId.takeIf { it.isNotBlank() }
                ?: existing?.productId
            val defaultProductId = preferredId
                ?.takeIf { candidate -> products.any { it.id == candidate } }
                ?: products.first().id

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (20 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            fun label(value: String) {
                layout.addView(TextView(context).apply { text = value })
            }

            label(
                "${station.name}\nChoose one or more items sold by this station and set a quantity for each. " +
                    "Each order uses Cash on Delivery " +
                    "at the station's current item price and delivery fee.\n"
            )
            label("Available items")
            val itemControls = products.map { product ->
                val savedItem = savedItems[product.id]
                val checked = if (existing != null && savedItems.isNotEmpty()) {
                    savedItem != null
                } else {
                    product.id == defaultProductId
                }
                val itemCheck = CheckBox(context).apply {
                    text = "${product.name} — ₱${String.format(Locale.getDefault(), "%.2f", product.price)}"
                    isChecked = checked
                }
                val itemQuantity = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    hint = "Quantity"
                    setText(
                        when {
                            savedItem != null -> savedItem.quantity
                            checkoutItem?.productId == product.id -> checkoutItem.quantity
                            product.id == defaultProductId -> initialQuantity
                            else -> 1
                        }.toString()
                    )
                    visibility = if (checked) View.VISIBLE else View.GONE
                }
                itemCheck.setOnCheckedChangeListener { _, isChecked ->
                    itemQuantity.visibility = if (isChecked) View.VISIBLE else View.GONE
                    if (isChecked) itemQuantity.requestFocus()
                }
                layout.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(itemCheck)
                    addView(itemQuantity)
                })
                ItemControl(product, itemCheck, itemQuantity)
            }

            label("Repeat every (days)")
            val interval = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText((existing?.repeatEveryDays ?: 7).toString())
            }
            layout.addView(interval)

            val formOpenedAt = System.currentTimeMillis()
            val nextDelivery = Calendar.getInstance(stationTimeZone).apply {
                timeInMillis = existing?.nextDeliveryAt
                    ?: DeliveryFinalization.firstDeliveryAfter(formOpenedAt, 7, hours)
            }
            val nextDate = Button(context).apply { isEnabled = false }
            val cutoffText = TextView(context)
            fun renderDate() {
                nextDate.text = "Next delivery: ${dateFormat.format(nextDelivery.time)}"
                cutoffText.text =
                    "The next delivery is calculated automatically from the repeat interval. " +
                    "Finalize changes by ${cutoffLabel(nextDelivery.timeInMillis)}, when the station opens. " +
                    "We will remind you the day before."
            }
            renderDate()
            interval.doAfterTextChanged { editable ->
                val days = editable.toString().toIntOrNull()
                if (days != null && days in 1..3650) {
                    nextDelivery.timeInMillis = if (
                        existing != null && days == existing.repeatEveryDays
                    ) {
                        existing.nextDeliveryAt
                    } else {
                        DeliveryFinalization.firstDeliveryAfter(formOpenedAt, days, hours)
                    }
                    renderDate()
                }
            }
            layout.addView(nextDate)
            layout.addView(cutoffText)

            if (inlineContainer != null && !inlineContainer.isAttachedToWindow) {
                return@addOnSuccessListener
            }
            val dialog = if (inlineContainer == null) {
                AlertDialog.Builder(context)
                    .setTitle("Recurring delivery")
                    .setView(ScrollView(context).apply { addView(layout) })
                    .setPositiveButton(if (existing == null) "Save recurring delivery" else "Save changes", null)
                    .setNegativeButton("Cancel") { _, _ -> onCancelled() }
                    .setOnCancelListener { onCancelled() }
                    .create()
            } else {
                null
            }
            dialog?.show()

            val saveButton = if (inlineContainer != null) {
                MaterialButton(context).apply {
                    text = "Save recurring delivery"
                    layout.addView(this)
                    inlineContainer.removeAllViews()
                    inlineContainer.addView(layout)
                }
            } else {
                dialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
            }

            fun persist(delivery: WeeklySubscription, orderToday: Boolean) {
                saveButton.isEnabled = false
                dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
                dialog?.setCancelable(false)
                onSavingChanged(true)
                val task = if (existing == null) {
                    FirebaseHelper.addSubscription(delivery, orderToday)
                } else {
                    FirebaseHelper.updateWeeklyDelivery(delivery, existing)
                }
                task.addOnSuccessListener {
                    dialog?.dismiss()
                    onSavingChanged(false)
                    if (orderToday) {
                        Toast.makeText(
                            context,
                            "Today's selected items were ordered. The next recurring delivery is ${dateFormat.format(Date(delivery.nextDeliveryAt))}.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onSaved()
                }.addOnFailureListener { error ->
                    saveButton.isEnabled = true
                    dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
                    dialog?.setCancelable(true)
                    onSavingChanged(false)
                    Toast.makeText(
                        context,
                        error.message ?: "Could not save recurring delivery",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            saveButton.setOnClickListener {
                val days = interval.text.toString().toIntOrNull() ?: 0
                if (days !in 1..3650) {
                    interval.error = "Enter 1 to 3650 days"
                    return@setOnClickListener
                }
                val selectedControls = itemControls.filter { it.selected.isChecked }
                if (selectedControls.isEmpty()) {
                    Toast.makeText(context, "Select at least one item", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                var invalidQuantity = false
                val selectedItems = selectedControls.mapNotNull { control ->
                    val count = control.quantity.text.toString().toIntOrNull() ?: 0
                    if (count <= 0) {
                        control.quantity.error = "Enter a positive quantity"
                        invalidQuantity = true
                        null
                    } else {
                        RecurringDeliveryItem(
                            productId = control.product.id,
                            productName = control.product.name,
                            quantity = count,
                            containerType = control.product.name
                        )
                    }
                }
                if (invalidQuantity) return@setOnClickListener

                val deliveryAt = if (existing != null && days == existing.repeatEveryDays) {
                    existing.nextDeliveryAt
                } else {
                    DeliveryFinalization.firstDeliveryAfter(System.currentTimeMillis(), days, hours)
                }
                if (!DeliveryFinalization.canEdit(deliveryAt, hours, System.currentTimeMillis())) {
                    Toast.makeText(
                        context,
                        "The station's opening-time cutoff has passed. Try saving again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                val firstItem = selectedItems.first()
                val delivery = (existing ?: WeeklySubscription()).copy(
                    customerUsername = customer.username,
                    stationOwnerUsername = station.username,
                    stationName = station.name,
                    productId = firstItem.productId,
                    productName = firstItem.productName,
                    containerType = firstItem.containerType,
                    quantity = firstItem.quantity,
                    items = selectedItems,
                    repeatEveryDays = days,
                    nextDeliveryAt = deliveryAt,
                    deliveryDay = "",
                    deliveryTimeSlot = "",
                    offeringType = OFFERING_PURCHASE,
                    refillServiceId = "",
                    refillInstructions = "",
                    emptyContainerCount = 0
                )

                if (existing != null || checkoutItem != null) {
                    persist(delivery, orderToday = false)
                    return@setOnClickListener
                }

                val orderSummary = selectedItems.joinToString("\n") {
                    "• ${it.quantity} × ${it.productName}"
                }
                AlertDialog.Builder(context)
                    .setTitle("Place these items today?")
                    .setMessage(
                        "$orderSummary\n\nWould you like to order the selected items today? " +
                            "Today's orders will use Cash on Delivery. Your recurring delivery will then continue every " +
                            "$days day(s), with the next one on ${dateFormat.format(Date(deliveryAt))}."
                    )
                    .setPositiveButton("Order today") { _, _ -> persist(delivery, orderToday = true) }
                    .setNegativeButton("Start on ${dateFormat.format(Date(deliveryAt))}") { _, _ ->
                        persist(delivery, orderToday = false)
                    }
                    .setNeutralButton("Back", null)
                    .show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Could not load station items", Toast.LENGTH_LONG).show()
            onCancelled()
        }
    }
}
