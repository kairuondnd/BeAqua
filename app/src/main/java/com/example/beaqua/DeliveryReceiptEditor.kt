package com.example.beaqua

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object DeliveryReceiptEditor {
    private data class Choice(val name: String, var quantity: Int, var selected: Boolean)
    private data class Row(
        val choice: Choice, val select: MaterialButton, val quantity: EditText,
        val minus: MaterialButton, val plus: MaterialButton
    )

    fun show(
        context: Context, group: OrderGroup,
        availableProducts: List<Product> = emptyList(),
        save: (List<DeliveryReceiptItem>) -> Task<DeliveryReceipt>,
        onSaved: (DeliveryReceipt) -> Unit
    ) {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        fun color(resource: Int) = ContextCompat.getColor(context, resource)
        val choices = linkedMapOf<String, Choice>()
        group.items.forEach { item ->
            val key = "${item.offeringType}:${item.productId.ifBlank { item.productName }}"
            val existing = choices[key]
            if (existing != null) {
                existing.quantity = (existing.quantity.toLong() + item.quantity).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } else {
                choices[key] = Choice(item.productName, item.quantity.coerceAtLeast(1), true)
            }
        }
        availableProducts.filter { it.ownerUsername == group.first.stationOwnerUsername && it.name.isNotBlank() }
            .forEach { product ->
                choices.putIfAbsent("$OFFERING_PURCHASE:${product.id.ifBlank { product.name }}", Choice(product.name, 1, false))
            }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(12))
        }
        content.addView(TextView(context).apply {
            text = "Select the delivered items, then adjust their quantities."
            textSize = 14f
            setTextColor(color(R.color.text_secondary))
            setPadding(0, 0, 0, dp(12))
        })
        val summary = TextView(context).apply {
            textSize = 13f
            setTextColor(color(R.color.primary))
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(summary)
        val rows = mutableListOf<Row>()
        val selectionError = TextView(context).apply {
            text = "Select at least one delivered item."
            setTextColor(color(R.color.error))
            visibility = View.GONE
        }
        fun updateSummary() {
            val selected = rows.filter { it.choice.selected }
            val quantity = selected.sumOf { it.quantity.text.toString().toIntOrNull()?.coerceAtLeast(0)?.toLong() ?: 0L }
            summary.text = "${selected.size} item(s) selected · $quantity total quantity"
            if (selected.isNotEmpty()) selectionError.visibility = View.GONE
        }
        choices.values.forEach { choice ->
            val card = MaterialCardView(context).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1).coerceAtLeast(1)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            }
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(12))
            }
            card.addView(body)
            val select = MaterialButton(context).apply {
                isCheckable = true
                isChecked = choice.selected
                isAllCaps = false
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                cornerRadius = dp(10)
                minHeight = dp(52)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            body.addView(select)
            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
            }
            controls.addView(TextView(context).apply {
                text = "Quantity"
                textSize = 14f
                setTextColor(color(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            fun stepButton(label: String, description: String) = MaterialButton(context).apply {
                text = label
                textSize = 22f
                contentDescription = description
                cornerRadius = dp(12)
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                insetTop = 0
                insetBottom = 0
                backgroundTintList = ColorStateList.valueOf(color(R.color.sky_blue_light))
                setTextColor(color(R.color.primary_variant))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
            val minus = stepButton("−", "Decrease ${choice.name} quantity")
            val quantity = EditText(context).apply {
                setText(choice.quantity.toString())
                hint = "Qty"
                contentDescription = "${choice.name} quantity"
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                gravity = Gravity.CENTER
                textSize = 18f
                setTextColor(color(R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(48)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
            }
            val plus = stepButton("+", "Increase ${choice.name} quantity")
            controls.addView(minus)
            controls.addView(quantity)
            controls.addView(plus)
            body.addView(controls)
            fun updateSelection() {
                select.text = if (choice.selected) "✓ ${choice.name}" else "+ ${choice.name}"
                select.contentDescription = "Select ${choice.name}"
                select.backgroundTintList = ColorStateList.valueOf(color(if (choice.selected) R.color.primary else R.color.gray_light))
                select.setTextColor(color(if (choice.selected) R.color.white else R.color.text_secondary))
                card.setCardBackgroundColor(color(if (choice.selected) R.color.sky_blue_light else R.color.white))
                card.strokeColor = color(if (choice.selected) R.color.primary else R.color.divider)
                controls.visibility = if (choice.selected) View.VISIBLE else View.GONE
                if (!choice.selected) quantity.error = null
                updateSummary()
            }
            rows.add(Row(choice, select, quantity, minus, plus))
            select.addOnCheckedChangeListener { _, checked ->
                choice.selected = checked
                updateSelection()
            }
            minus.setOnClickListener {
                quantity.setText(((quantity.text.toString().toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString())
            }
            plus.setOnClickListener {
                val current = quantity.text.toString().toIntOrNull() ?: 0
                quantity.setText((current.toLong() + 1).coerceIn(1L, Int.MAX_VALUE.toLong()).toString())
            }
            quantity.doAfterTextChanged {
                quantity.error = null
                updateSummary()
            }
            updateSelection()
            content.addView(card)
        }
        content.addView(selectionError)
        updateSummary()
        val scroll = ScrollView(context).apply { addView(content) }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Confirm delivery receipt")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm & generate", null)
            .create()
        dialog.setOnShowListener {
            val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            fun setSaving(saving: Boolean) {
                confirm.isEnabled = !saving
                cancel.isEnabled = !saving
                dialog.setCancelable(!saving)
                rows.forEach { row ->
                    row.select.isEnabled = !saving
                    row.quantity.isEnabled = !saving
                    row.minus.isEnabled = !saving
                    row.plus.isEnabled = !saving
                }
            }
            confirm.setOnClickListener {
                val selected = rows.filter { it.choice.selected }
                if (selected.isEmpty()) {
                    selectionError.visibility = View.VISIBLE
                    scroll.post { scroll.smoothScrollTo(0, selectionError.bottom) }
                    return@setOnClickListener
                }
                var valid = true
                val items = selected.map { row ->
                    val count = row.quantity.text.toString().trim().toIntOrNull()
                    row.quantity.error = if (count == null || count <= 0) "Enter a positive whole number" else null
                    if (row.quantity.error != null) valid = false
                    DeliveryReceiptItem(row.choice.name, count ?: 0)
                }
                if (!valid) return@setOnClickListener
                setSaving(true)
                save(items).addOnSuccessListener { receipt ->
                    dialog.dismiss()
                    onSaved(receipt)
                }.addOnFailureListener { error ->
                    setSaving(false)
                    Toast.makeText(context, error.message ?: "Could not confirm delivery. Try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }
}