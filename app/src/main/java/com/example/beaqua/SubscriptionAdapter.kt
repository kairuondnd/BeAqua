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
import java.util.TimeZone

class SubscriptionAdapter(
    private val subscriptions: List<WeeklySubscription>,
    private val onActiveChanged: (WeeklySubscription, Boolean) -> Unit,
    private val onCancel: (WeeklySubscription) -> Unit,
    private val onEdit: (WeeklySubscription) -> Unit,
    private val stationHours: Map<String, OperatingHours> = emptyMap()
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
        val items = subscription.deliveryItems()

        holder.product.text = if (items.size == 1) {
            items.first().productName
        } else {
            "${items.size} recurring items"
        }
        holder.station.text = subscription.stationName
        holder.schedule.text =
            "Every ${subscription.repeatEveryDays} day(s)"
        holder.details.text = items.joinToString("\n") {
            "${it.quantity} × ${it.productName}"
        } + "\nNext: $nextDate"
        holder.status.text = if (subscription.active) subscription.lastStatus else "Paused"
        stationHours[subscription.stationOwnerUsername]?.let { hours ->
            val cutoff = DeliveryFinalization.cutoff(subscription.nextDeliveryAt, hours)
            val deadline = SimpleDateFormat("MMM d, h:mm a z", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone(hours.timeZoneId)
            }.format(Date(cutoff))
            if (subscription.active) {
                holder.status.text = if (System.currentTimeMillis() < cutoff) {
                    "Finalize by $deadline"
                } else {
                    "Finalized at $deadline"
                }
            }
        }

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
