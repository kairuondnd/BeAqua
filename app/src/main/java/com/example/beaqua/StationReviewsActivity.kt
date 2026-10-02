package com.example.beaqua

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

/** Customer-facing, read-only reviews for exactly one water station. */
class StationReviewsActivity : AppCompatActivity() {
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackNavigation.install(this)
        val stationUsername = intent.getStringExtra("STATION_USERNAME").orEmpty()
        if (stationUsername.isBlank()) { finish(); return }

        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setBackgroundColor(getColor(R.color.white))
        }
        header.addView(MaterialButton(this).apply {
            text = "Back to station"
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        })
        header.addView(TextView(this).apply {
            text = "Customer reviews"
            textSize = 22f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = intent.getStringExtra("STATION_NAME")?.takeIf { it.isNotBlank() } ?: stationUsername
            textSize = 16f
            setTextColor(getColor(R.color.text_secondary))
        })
        val summary = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(12), 0, dp(8))
            setTextColor(getColor(R.color.text_primary))
        }
        header.addView(summary)
        val progress = ProgressBar(this)
        val message = TextView(this).apply {
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setTextColor(getColor(R.color.text_secondary))
            visibility = View.GONE
        }
        val retry = MaterialButton(this).apply { text = "Retry"; visibility = View.GONE }
        val reviews = mutableListOf<Feedback>()
        val adapter = FeedbackAdapter(reviews)
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StationReviewsActivity)
            this.adapter = adapter
        }
        root.addView(header)
        root.addView(progress)
        root.addView(message)
        root.addView(retry)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        fun listen() {
            listener?.remove()
            progress.visibility = View.VISIBLE
            retry.visibility = View.GONE
            message.visibility = View.GONE
            listener = FirebaseHelper.feedbacksCollection
                .whereEqualTo("stationOwnerUsername", stationUsername)
                .addSnapshotListener { snapshot, error ->
                    if (isDestroyed || isFinishing) return@addSnapshotListener
                    progress.visibility = View.GONE
                    if (error != null || snapshot == null) {
                        message.text = "Could not load reviews. Check your connection and try again."
                        message.visibility = View.VISIBLE
                        retry.visibility = View.VISIBLE
                        return@addSnapshotListener
                    }
                    reviews.clear()
                    retry.visibility = View.GONE
                    reviews.addAll(snapshot.toObjects(Feedback::class.java).sortedByDescending { it.timestamp })
                    adapter.notifyDataSetChanged()
                    fun average(values: List<Float>): String {
                        val ratings = values.filter { it in 1f..5f }
                        return if (ratings.isEmpty()) "Not rated yet"
                            else String.format(Locale.getDefault(), "%.1f / 5 (%d ratings)", ratings.average(), ratings.size)
                    }
                    summary.text = "${reviews.size} review(s) · Newest first\n" +
                        "Product quality: ${average(reviews.map { it.productQualityRating })}\n" +
                        "Service quality: ${average(reviews.map { it.serviceQualityRating })}"
                    message.text = "No reviews yet for this station."
                    message.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
                }
        }
        retry.setOnClickListener { listen() }
        listen()
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
