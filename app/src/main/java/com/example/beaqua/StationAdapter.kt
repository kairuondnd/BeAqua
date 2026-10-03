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
    favoriteUsernames: List<String>,
    private val customerLatitude: Double?,
    private val customerLongitude: Double?,
    private val saveFavorite: (User, Boolean) -> com.google.android.gms.tasks.Task<Void>,
    private val onViewInventory: (User) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {
    private val favorites = favoriteUsernames.toMutableSet()
    private val saving = mutableSetOf<String>()
    private var displayedStations = sortedStations()

    private fun sortedStations() = stations.sortedWith(
        compareBy<User> { if (it.username in favorites) 0 else 1 }
            .thenBy { distanceMeters(it) }
    )

    private fun distanceMeters(station: User): Double {
        val lat = customerLatitude ?: return Double.POSITIVE_INFINITY
        val lon = customerLongitude ?: return Double.POSITIVE_INFINITY
        val stationLat = station.latitude ?: return Double.POSITIVE_INFINITY
        val stationLon = station.longitude ?: return Double.POSITIVE_INFINITY
        fun valid(latitude: Double, longitude: Double) = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
        if (!valid(lat, lon) || !valid(stationLat, stationLon)) return Double.POSITIVE_INFINITY
        val result = FloatArray(1)
        android.location.Location.distanceBetween(lat, lon, stationLat, stationLon, result)
        return result[0].toDouble()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(displayedStations[position])
    }

    override fun getItemCount(): Int = stations.size

    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStationName: TextView = itemView.findViewById(R.id.tvStationName)
        private val tvStationAddress: TextView = itemView.findViewById(R.id.tvStationAddress)
        private val tvOperatingStatus: TextView =
            itemView.findViewById(R.id.tvStationOperatingStatus)
        private val btnViewInventory: Button = itemView.findViewById(R.id.btnViewInventory)

        fun bind(station: User) {
            val favorite = itemView.findViewById<android.widget.ImageButton>(R.id.btnFavoriteStation)
            val selected = station.username in favorites
            favorite.setImageResource(if (selected) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
            favorite.contentDescription = if (selected) "Remove ${station.name} from favorites" else "Favorite ${station.name}"
            favorite.isEnabled = station.username !in saving
            favorite.setOnClickListener {
                if (!saving.add(station.username)) return@setOnClickListener
                favorite.isEnabled = false
                saveFavorite(station, !selected).addOnSuccessListener {
                    if (selected) favorites.remove(station.username) else favorites.add(station.username)
                    saving.remove(station.username)
                    displayedStations = sortedStations()
                    notifyDataSetChanged()
                }.addOnFailureListener {
                    saving.remove(station.username)
                    notifyDataSetChanged()
                    android.widget.Toast.makeText(itemView.context, "Could not save favorite. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
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
