package com.example.beaqua

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var username: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private val subscriptions = mutableListOf<WeeklySubscription>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscriptions)

        username = intent.getStringExtra("USERNAME").orEmpty()
        if (username.isEmpty()) {
            finish()
            return
        }

        recyclerView = findViewById(R.id.rvSubscriptions)
        emptyState = findViewById(R.id.emptySubscriptions)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnBackSubscriptions).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnBrowseForSubscription).setOnClickListener { finish() }
        loadSubscriptions()
    }

    private fun loadSubscriptions() {
        FirebaseHelper.getSubscriptionsForCustomer(username)
            .addOnSuccessListener { result ->
                subscriptions.clear()
                subscriptions.addAll(
                    result.toObjects(WeeklySubscription::class.java)
                        .sortedWith(
                            compareByDescending<WeeklySubscription> { it.active }
                                .thenBy { it.nextDeliveryAt }
                        )
                )
                recyclerView.adapter = SubscriptionAdapter(
                    subscriptions,
                    onActiveChanged = { subscription, active ->
                        setSubscriptionActive(subscription, active)
                    },
                    onCancel = { subscription -> confirmCancel(subscription) }
                )
                updateEmptyState()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to load weekly deliveries", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setSubscriptionActive(subscription: WeeklySubscription, active: Boolean) {
        FirebaseHelper.updateSubscriptionActive(subscription.id, active)
            .addOnSuccessListener {
                subscription.active = active
                subscription.lastStatus = if (active) "Scheduled" else "Paused"
                recyclerView.adapter?.notifyItemChanged(subscriptions.indexOf(subscription))
            }
            .addOnFailureListener {
                recyclerView.adapter?.notifyItemChanged(subscriptions.indexOf(subscription))
                Toast.makeText(
                    this,
                    it.message ?: "Could not update subscription",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun confirmCancel(subscription: WeeklySubscription) {
        AlertDialog.Builder(this)
            .setTitle("Cancel weekly delivery?")
            .setMessage("${subscription.productName} will no longer be ordered automatically.")
            .setPositiveButton("Cancel subscription") { _, _ ->
                FirebaseHelper.deleteSubscription(subscription.id)
                    .addOnSuccessListener {
                        val index = subscriptions.indexOf(subscription)
                        subscriptions.remove(subscription)
                        recyclerView.adapter?.notifyItemRemoved(index)
                        updateEmptyState()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Could not cancel subscription", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Keep it", null)
            .show()
    }

    private fun updateEmptyState() {
        val isEmpty = subscriptions.isEmpty()
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}
