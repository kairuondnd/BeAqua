package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class StationOwnerHistoryAdapter(
    private val orders: List<Order>
) : RecyclerView.Adapter<StationOwnerHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvCustomerName: TextView = itemView.findViewById(R.id.tvStationOwner) // reuse existing TextView
        val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        val tvOrderDetails: TextView = itemView.findViewById(R.id.tvOrderDetails)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val btnPrintReceipt: MaterialButton = itemView.findViewById(R.id.btnPrintReceipt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val order = orders[position]
        val context = holder.itemView.context
        
        var nameText = order.productName
        if (order.isRefill()) {
            nameText = "[REFILL] $nameText"
        }
        if (order.isSubscriptionOrder) {
            nameText = "[WEEKLY] $nameText"
        }
        if (order.isRushOrder) {
            nameText = "[RUSH] $nameText"
            holder.tvProductName.setTextColor(ContextCompat.getColor(context, R.color.warning))
        } else {
            holder.tvProductName.setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        
        holder.tvProductName.text = nameText
        holder.tvCustomerName.text = "Customer: ${order.customerName}"
        holder.tvQuantity.text = if (order.isRefill()) {
            "Empty containers: ${order.emptyContainerCount.coerceAtLeast(order.quantity)} | Total: ₱${String.format("%.2f", order.totalPrice)}"
        } else {
            "Quantity: ${order.quantity} | Total: ₱${String.format("%.2f", order.totalPrice)}"
        }
        
        var details =
            "Type: ${order.containerType} | Estimated delivery: ${order.deliveryTimeSlot}"
        if (order.isRushOrder) {
            details += "\nRush Fee included: ₱${String.format("%.2f", order.rushOrderFee)}"
        }
        if (order.isRefill() && order.refillInstructions.isNotBlank()) {
            details += "\nNotes: ${order.refillInstructions}"
        }
        holder.tvOrderDetails.text = details
        holder.tvStatus.text = "Status: ${order.status}"

        // Receipt Printing Logic
        if (order.isPaid) {
            holder.btnPrintReceipt.visibility = View.VISIBLE
            holder.btnPrintReceipt.setOnClickListener {
                ReceiptHelper.printReceipt(context, order)
            }
        } else {
            holder.btnPrintReceipt.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = orders.size
}
