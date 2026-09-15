package com.example.beaqua

import android.app.DatePickerDialog
import android.content.Context
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object RecurringDeliveryEditor {
    fun show(context: Context, customer: User, station: User, existing: WeeklySubscription? = null,
             preferredProductId: String = "", initialQuantity: Int = 1, onSaved: () -> Unit) {
        if (customer.address.isBlank()) {
            Toast.makeText(context, "Add your delivery address in Profile first", Toast.LENGTH_LONG).show()
            return
        }
        FirebaseHelper.getProductsByStation(station.username).addOnSuccessListener { result ->
            val products = result.toObjects(Product::class.java).toMutableList()
            val refillId = "REFILL_${station.username}"
            if (station.refillServiceEnabled && station.refillFee > 0.0) {
                products.add(Product(id = refillId, name = "Water refill (your containers)",
                    price = station.refillFee, ownerUsername = station.username))
            }
            if (products.isEmpty() || !station.isApprovedStationOwner()) {
                Toast.makeText(context, "No delivery options are available at this station", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (20 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            fun label(text: String) { layout.addView(TextView(context).apply { this.text = text }) }
            label("${station.name}\nNo subscription fee. Each delivery is paid by Cash on Delivery at current station prices.\n")
            label("Bottle type / service")
            val offering = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                    products.map { "${it.name} — ₱${it.price}" })
                setSelection(products.indexOfFirst { it.id == (existing?.productId ?: preferredProductId) }.coerceAtLeast(0))
            }
            layout.addView(offering)
            label("Number of bottles / containers")
            val quantity = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText((existing?.quantity ?: initialQuantity).toString())
            }
            layout.addView(quantity)
            label("Repeat every (days)")
            val interval = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText((existing?.repeatEveryDays ?: 7).toString())
            }
            layout.addView(interval)
            val date = Calendar.getInstance().apply {
                if (existing != null && existing.nextDeliveryAt > System.currentTimeMillis()) {
                    timeInMillis = existing.nextDeliveryAt
                } else {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
            }
            val dateButton = Button(context)
            fun renderDate() { dateButton.text = "Next delivery: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date.time) }
            renderDate()
            dateButton.setOnClickListener {
                DatePickerDialog(context, { _, year, month, day ->
                    date.set(year, month, day, 6, 0, 0)
                    date.set(Calendar.MILLISECOND, 0)
                    renderDate()
                }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show()
            }
            layout.addView(dateButton)
            label("Preferred delivery window")
            val windows = station.etaSettings.customerDeliveryWindows()
            val window = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, windows)
                setSelection(windows.indexOf(existing?.deliveryTimeSlot).coerceAtLeast(0))
            }
            layout.addView(window)
            label("Refill instructions (optional; used for refills)")
            val notes = EditText(context).apply { setText(existing?.refillInstructions.orEmpty()) }
            layout.addView(notes)
            val dialog = AlertDialog.Builder(context).setTitle("Customize automated delivery")
                .setView(ScrollView(context).apply { addView(layout) })
                .setPositiveButton("Save schedule", null).setNegativeButton("Cancel", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val count = quantity.text.toString().toIntOrNull() ?: 0
                    val days = interval.text.toString().toIntOrNull() ?: 0
                    if (count <= 0) { quantity.error = "Enter a positive quantity"; return@setOnClickListener }
                    if (days !in 1..3650) { interval.error = "Enter 1 to 3650 days"; return@setOnClickListener }
                    if (date.timeInMillis <= System.currentTimeMillis()) {
                        Toast.makeText(context, "Choose a future delivery date", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val selectedWindow = windows.getOrNull(window.selectedItemPosition) ?: return@setOnClickListener
                    val product = products[offering.selectedItemPosition]
                    val refill = product.id == refillId
                    val delivery = (existing ?: WeeklySubscription()).copy(
                        customerUsername = customer.username, stationOwnerUsername = station.username,
                        stationName = station.name, productId = product.id, productName = product.name,
                        containerType = if (refill) "Customer-owned container" else product.name,
                        quantity = count, repeatEveryDays = days, nextDeliveryAt = date.timeInMillis,
                        deliveryDay = "", deliveryTimeSlot = selectedWindow,
                        offeringType = if (refill) OFFERING_REFILL else OFFERING_PURCHASE,
                        refillServiceId = if (refill) station.username else "",
                        refillInstructions = if (refill) notes.text.toString().trim() else "",
                        emptyContainerCount = if (refill) count else 0)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    val task = if (existing == null) FirebaseHelper.addSubscription(delivery)
                        else FirebaseHelper.updateWeeklyDelivery(delivery)
                    task.addOnSuccessListener { dialog.dismiss(); onSaved() }.addOnFailureListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(context, it.message ?: "Could not save schedule", Toast.LENGTH_LONG).show()
                    }
                }
            }
            dialog.show()
        }.addOnFailureListener {
            Toast.makeText(context, "Could not load station bottle types", Toast.LENGTH_LONG).show()
        }
    }
}
