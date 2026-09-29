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
        val product: TextView = itemView.findViewById(R.id.tvFeedbackProduct)
        val productLabel: TextView = itemView.findViewById(R.id.tvProductQualityLabel)
        val productRating: RatingBar = itemView.findViewById(R.id.ratingProductQualityView)
        val serviceLabel: TextView = itemView.findViewById(R.id.tvServiceQualityLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedbackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feedback, parent, false)
        return FeedbackViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedbackViewHolder, position: Int) {
        val feedback = feedbackList[position]
        holder.tvCustomer.text = "From: ${feedback.customerUsername}"
        
        val sdf = SimpleDateFormat("MMM dd, yyyy h:mma", Locale.US)
        holder.tvDate.text = sdf.format(Date(feedback.timestamp))
        
        holder.product.text = feedback.productName
        holder.product.visibility = if (feedback.productName.isBlank()) View.GONE else View.VISIBLE
        holder.productLabel.text = if (feedback.productQualityRating > 0f)
            "Product quality: ${feedback.productQualityRating}/5" else "Product quality: not rated in this older review"
        holder.productRating.rating = feedback.productQualityRating
        holder.productRating.visibility = if (feedback.productQualityRating > 0f) View.VISIBLE else View.GONE
        holder.serviceLabel.text = if (feedback.serviceQualityRating > 0f)
            "Service quality: ${feedback.serviceQualityRating}/5" else "Overall station rating (older review)"
        holder.ratingStation.rating = if (feedback.serviceQualityRating > 0f) feedback.serviceQualityRating else feedback.stationRating
        
        if (feedback.remarks.isNotEmpty()) {
            holder.tvRemarks.text = "\"${feedback.remarks}\""
            holder.tvRemarks.visibility = View.VISIBLE
        } else {
            holder.tvRemarks.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = feedbackList.size
}
