package com.example.beaqua

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserHistoryAdapter(
    private var historyList: List<Order>,
    private val role: String = "User",
    private val onRateClick: (List<Order>) -> Unit
) : RecyclerView.Adapter<UserHistoryAdapter.HistoryViewHolder>() {
    private var groups = groupOrderHistory(historyList)

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvStationOwner: TextView = itemView.findViewById(R.id.tvStationOwner)
        val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        val tvTotalPrice: TextView = itemView.findViewById(R.id.tvTotalPrice)
        val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderDate)
        val tvOrderDetails: TextView = itemView.findViewById(R.id.tvOrderDetails)
        val tvPaymentMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvCustomerInfo: TextView = itemView.findViewById(R.id.tvCustomerInfo)
        val layoutCustomerInfo: LinearLayout = itemView.findViewById(R.id.layoutCustomerInfo)
        val btnRateOrder: MaterialButton = itemView.findViewById(R.id.btnRateOrder)
        val btnPrintReceipt: MaterialButton = itemView.findViewById(R.id.btnPrintReceipt)
        val tvAlreadyRated: TextView = itemView.findViewById(R.id.tvAlreadyRated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    fun updateData(newList: List<Order>) {
        this.historyList = newList.toList()
        groups = groupOrderHistory(historyList)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val group = groups[position]
        val order = group.first
        val context = holder.itemView.context
        
        val servicePrefix = if (order.isRefill()) "REFILL • " else ""
        holder.tvProductName.text =
            if (order.isSubscriptionOrder) "AUTOMATED • $servicePrefix${order.productName}"
            else "$servicePrefix${order.productName}"
        holder.tvStationOwner.text = order.stationName.ifEmpty { "Station: ${order.stationOwnerUsername}" }
        holder.tvQuantity.text = if (order.isRefill()) {
            "${order.emptyContainerCount.coerceAtLeast(order.quantity)} Empty Containers"
        } else {
            "${order.quantity} Units"
        }
        holder.tvTotalPrice.text = String.format(Locale.getDefault(), "₱%.2f", order.totalPrice)
        
        val date = Date(order.timestamp)
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        holder.tvOrderDate.text = sdf.format(date)

        holder.tvOrderDetails.text =
            order.containerType +
            if (order.isRefill() && order.refillInstructions.isNotBlank()) {
                "\nNotes: ${order.refillInstructions}"
            } else {
                ""
            } + if (order.estimatedDeliveryDate > 0L) {
                "\nEstimated delivery: ${DeliveryEta.label(order.estimatedDeliveryDate, order.estimatedDeliveryTimeZoneId)}"
            } else {
                ""
            }
        holder.tvPaymentMethod.text = "Payment: ${order.paymentMethod}${if (order.isPaid) " (Paid)" else ""}"
        holder.tvStatus.text = order.status.uppercase()

        val statuses = group.items.map { it.status }.distinct()
        holder.tvProductName.text = if (group.items.size == 1) holder.tvProductName.text
            else if (order.isSubscriptionOrder) "Recurring order" else "Order"
        holder.tvQuantity.text = "${group.items.sumOf { it.quantity }} units · ${group.items.size} item(s)"
        holder.tvTotalPrice.text = String.format(Locale.getDefault(), "₱%.2f", group.totalPrice)
        holder.tvOrderDetails.text = group.itemSummary() +
            if (statuses.size > 1) "\n" + group.items.joinToString("\n") { "${it.productName}: ${it.status}" }
            else if (order.estimatedDeliveryDate > 0L)
                "\nEstimated delivery: ${DeliveryEta.label(order.estimatedDeliveryDate, order.estimatedDeliveryTimeZoneId)}"
            else ""
        holder.tvPaymentMethod.text = "Payment: ${group.items.map { it.paymentMethod }.distinct().joinToString(", ")}" +
            if (group.isPaid) " (Paid)" else if (group.items.any { it.isPaid }) " (Partially paid)" else ""
        holder.tvStatus.text = if (statuses.size == 1) statuses.first().uppercase() else "MIXED STATUS"
        
        val cancelled = group.items.filter { it.status == "Cancelled" || it.status == "Canceled" }
        if (cancelled.isNotEmpty()) {
            val reasons = cancelled.groupBy { item ->
                item.cancellationReason.ifBlank {
                    if (item.autoCancelledAt > 0L)
                        "Automatically cancelled because the station did not accept it before the acceptance deadline."
                    else "No cancellation reason was recorded for this order."
                }
            }
            reasons.forEach { (reason, items) ->
                val affected = if (cancelled.size != group.items.size || reasons.size > 1)
                    " (${items.joinToString(", ") { it.productName }})" else ""
                holder.tvOrderDetails.append("\n\nCancellation reason$affected: $reason")
            }
        }
        val rejected = group.items.firstOrNull { it.status == "Rejected" && it.rejectionReason.isNotBlank() }
        val chat = holder.itemView.findViewById<MaterialButton>(R.id.btnRejectionChat)
        chat.visibility = if (rejected != null && role == "User") View.VISIBLE else View.GONE
        if (rejected != null) {
            holder.tvOrderDetails.append("\n\nRejection reason: ${rejected.rejectionReason}\nFor any order or payment concerns, contact the water station owner through chat.")
            chat.setOnClickListener {
                context.startActivity(android.content.Intent(context, SingleChatActivity::class.java)
                    .putExtra("CURRENT_USERNAME", rejected.customerName)
                    .putExtra("CHAT_WITH_USERNAME", rejected.stationOwnerUsername))
            }
        } else chat.setOnClickListener(null)

        // Customer Info Logic (Station Owner View)
        if (role != "User") {
            holder.layoutCustomerInfo.visibility = View.VISIBLE
            holder.tvCustomerInfo.text = "Customer: ${order.customerName}"
        } else {
            holder.layoutCustomerInfo.visibility = View.GONE
        }

        // Rating Logic
        val deliveredItems = group.items.filter { it.status == "Delivered" }
        val unrated = deliveredItems.filterNot { it.isRated }
        if (role == "User" && deliveredItems.isNotEmpty()) {
            if (unrated.isEmpty()) {
                holder.btnRateOrder.visibility = View.GONE
                holder.tvAlreadyRated.visibility = View.VISIBLE
            } else {
                holder.btnRateOrder.visibility = View.VISIBLE
                holder.tvAlreadyRated.visibility = View.GONE
                holder.btnRateOrder.setOnClickListener {
                    onRateClick(deliveredItems)
                }
            }
        } else {
            holder.btnRateOrder.visibility = View.GONE
            holder.tvAlreadyRated.visibility = View.GONE
        }

        // Receipt Printing Logic
        if (group.isPaid) {
            holder.btnPrintReceipt.visibility = View.VISIBLE
            holder.btnPrintReceipt.setOnClickListener {
                ReceiptHelper.printReceipt(context, group.items)
            }
        } else {
            holder.btnPrintReceipt.visibility = View.GONE
        }
        
        val statusColor: Int
        val statusBg: String
        when (statuses.singleOrNull()) {
            "Delivered" -> { statusColor = R.color.success; statusBg = "#F0FDF4" }
            "Pending" -> { statusColor = R.color.warning; statusBg = "#FFFBEB" }
            "Accepted" -> { statusColor = R.color.primary; statusBg = "#EFF6FF" }
            "Rejected" -> { statusColor = R.color.error; statusBg = "#FEF2F2" }
            "Cancelled" -> { statusColor = R.color.error; statusBg = "#FEF2F2" }
            else -> { statusColor = R.color.text_secondary; statusBg = "#F9FAFB" }
        }
        
        holder.tvStatus.setTextColor(ContextCompat.getColor(context, statusColor))
        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor(statusBg))
    }

    override fun getItemCount(): Int = groups.size
}
