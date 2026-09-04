package com.example.beaqua

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private val stations: List<User>,
    private val onViewInventory: (User) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(stations[position])
    }

    override fun getItemCount(): Int = stations.size

    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStationName: TextView = itemView.findViewById(R.id.tvStationName)
        private val tvStationAddress: TextView = itemView.findViewById(R.id.tvStationAddress)
        private val tvOperatingStatus: TextView =
            itemView.findViewById(R.id.tvStationOperatingStatus)
        private val btnViewInventory: Button = itemView.findViewById(R.id.btnViewInventory)

        fun bind(station: User) {
            tvStationName.text = station.name
            tvStationAddress.text = station.address.ifEmpty { "Address not set" }
            val isOpen = station.isStationOpen()
            tvOperatingStatus.text =
                "${station.stationStatusLabel()} • ${station.operatingHoursLabel()}"
            tvOperatingStatus.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (isOpen) R.color.success else R.color.error
                )
            )
            tvOperatingStatus.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    itemView.context,
                    if (isOpen) R.color.mint_soft else R.color.peach_soft
                )
            )
            btnViewInventory.setOnClickListener {
                onViewInventory(station)  // Calls showStationInventory in UserHomeActivity
            }
        }
    }
}
