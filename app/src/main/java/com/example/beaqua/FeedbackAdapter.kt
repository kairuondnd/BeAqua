package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackAdapter(private val feedbackList: List<Feedback>) :
    RecyclerView.Adapter<FeedbackAdapter.FeedbackViewHolder>() {

    class FeedbackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomer: TextView = itemView.findViewById(R.id.tvFeedbackCustomer)
        val tvDate: TextView = itemView.findViewById(R.id.tvFeedbackDate)
        val ratingStation: RatingBar = itemView.findViewById(R.id.ratingStationView)
        val tvRemarks: TextView = itemView.findViewById(R.id.tvFeedbackRemarks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedbackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feedback, parent, false)
        return FeedbackViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedbackViewHolder, position: Int) {
        val feedback = feedbackList[position]
        holder.tvCustomer.text = "From: ${feedback.customerUsername}"
        
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(feedback.timestamp))
        
        holder.ratingStation.rating = feedback.stationRating
        
        if (feedback.remarks.isNotEmpty()) {
            holder.tvRemarks.text = "\"${feedback.remarks}\""
            holder.tvRemarks.visibility = View.VISIBLE
        } else {
            holder.tvRemarks.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = feedbackList.size
}
