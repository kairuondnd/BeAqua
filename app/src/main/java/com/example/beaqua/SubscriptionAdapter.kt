package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionAdapter(
    private val subscriptions: List<WeeklySubscription>,
    private val onActiveChanged: (WeeklySubscription, Boolean) -> Unit,
    private val onCancel: (WeeklySubscription) -> Unit,
    private val onEdit: (WeeklySubscription) -> Unit
) : RecyclerView.Adapter<SubscriptionAdapter.SubscriptionViewHolder>() {

    class SubscriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val product: TextView = view.findViewById(R.id.tvSubscriptionProductName)
        val station: TextView = view.findViewById(R.id.tvSubscriptionStation)
        val schedule: TextView = view.findViewById(R.id.tvSubscriptionSchedule)
        val details: TextView = view.findViewById(R.id.tvSubscriptionDetails)
        val status: TextView = view.findViewById(R.id.tvSubscriptionStatus)
        val active: SwitchMaterial = view.findViewById(R.id.switchSubscriptionActive)
        val cancel: MaterialButton = view.findViewById(R.id.btnCancelSubscription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weekly_subscription, parent, false)
        return SubscriptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val subscription = subscriptions[position]
        val nextDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(subscription.nextDeliveryAt))

        holder.product.text = if (subscription.offeringType == OFFERING_REFILL) {
            "REFILL • ${subscription.productName}"
        } else {
            subscription.productName
        }
        holder.station.text = subscription.stationName
        holder.schedule.text =
            "Every ${subscription.repeatEveryDays} day(s) • Delivery window: ${subscription.deliveryTimeSlot}"
        holder.details.text = if (subscription.offeringType == OFFERING_REFILL) {
            "${subscription.emptyContainerCount.coerceAtLeast(subscription.quantity)} empty containers • Next: $nextDate"
        } else {
            "${subscription.quantity} × ${subscription.containerType} • Next: $nextDate"
        }
        holder.status.text = if (subscription.active) subscription.lastStatus else "Paused"

        holder.active.setOnCheckedChangeListener(null)
        holder.active.isChecked = subscription.active
        holder.active.setOnCheckedChangeListener { _, checked ->
            onActiveChanged(subscription, checked)
        }
        holder.cancel.setOnClickListener { onCancel(subscription) }
        holder.itemView.findViewById<MaterialButton>(R.id.btnEditWeeklyDelivery)
            .setOnClickListener { onEdit(subscription) }
    }

    override fun getItemCount(): Int = subscriptions.size
}
