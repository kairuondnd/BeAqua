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
    private var reminderOpened = false
    private val stationHours = mutableMapOf<String, OperatingHours>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscriptions)

        username = intent.getStringExtra("USERNAME").orEmpty()
        if (intent.hasExtra("REMINDER_SUBSCRIPTION_ID") &&
            getSharedPreferences("delivery_reminders", MODE_PRIVATE).getString("customer", null) != username) {
            Toast.makeText(this, "Sign in to review your automated delivery", Toast.LENGTH_LONG).show()
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        if (username.isEmpty()) {
            finish()
            return
        }

        recyclerView = findViewById(R.id.rvSubscriptions)
        emptyState = findViewById(R.id.emptySubscriptions)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnBackSubscriptions).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnBrowseForSubscription).setOnClickListener { finish() }
        reminderOpened = savedInstanceState?.getBoolean("reminderOpened") ?: false
        FirebaseHelper.processDueSubscriptions(customerUsername = username).addOnCompleteListener { loadSubscriptions() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("reminderOpened", reminderOpened)
        super.onSaveInstanceState(outState)
    }

    private fun loadSubscriptions() {
        FirebaseHelper.getSubscriptionsForCustomer(username)
            .addOnSuccessListener { result ->
                subscriptions.clear()
                subscriptions.addAll(
                    result.toObjects(WeeklySubscription::class.java)
                        .filter { intent.getStringExtra("STATION_USERNAME").let { station ->
                            station.isNullOrBlank() || it.stationOwnerUsername == station
                        } }
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
                    onCancel = { subscription -> confirmCancel(subscription) },
                    onEdit = { subscription -> editDelivery(subscription) },
                    stationHours = stationHours
                )
                subscriptions.map { it.stationOwnerUsername }.distinct().forEach { stationUsername ->
                    FirebaseHelper.getUser(stationUsername).addOnSuccessListener { snapshot ->
                        snapshot.toObject(User::class.java)?.let {
                            stationHours[stationUsername] = it.operatingHours
                            recyclerView.adapter?.notifyDataSetChanged()
                        }
                    }
                }
                updateEmptyState()
                if (!reminderOpened) {
                    reminderOpened = true
                    subscriptions.find { it.id == intent.getStringExtra("REMINDER_SUBSCRIPTION_ID") }
                        ?.let {
                            if (it.nextDeliveryAt == intent.getLongExtra("REMINDER_DELIVERY_AT", 0L) && it.active) {
                                editDelivery(it)
                            } else {
                                Toast.makeText(this, "That delivery has changed or was finalized. Your current schedules are shown here.", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to load automated deliveries", Toast.LENGTH_SHORT).show()
            }
    }

    private fun editDelivery(subscription: WeeklySubscription) {
        FirebaseHelper.getUser(username).addOnSuccessListener { customerSnapshot ->
            val customer = customerSnapshot.toObject(User::class.java) ?: return@addOnSuccessListener
            FirebaseHelper.getUser(subscription.stationOwnerUsername).addOnSuccessListener { stationSnapshot ->
                val station = stationSnapshot.toObject(User::class.java) ?: return@addOnSuccessListener
                RecurringDeliveryEditor.show(this, customer, station, existing = subscription,
                    onSaved = { loadSubscriptions() })
            }.addOnFailureListener {
                Toast.makeText(this, "Could not load station", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Could not load customer", Toast.LENGTH_LONG).show()
        }
    }

    private fun setSubscriptionActive(subscription: WeeklySubscription, active: Boolean) {
        FirebaseHelper.updateSubscriptionActive(subscription.id, active)
            .addOnSuccessListener {
                // Resuming can advance nextDeliveryAt; render the saved date, not the stale local copy.
                loadSubscriptions()
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
            .setTitle("Cancel automated delivery?")
            .setMessage(
                subscription.deliveryItems().joinToString(", ") { it.productName } +
                    " will no longer be ordered automatically."
            )
            .setPositiveButton("Remove delivery") { _, _ ->
                FirebaseHelper.deleteSubscription(subscription.id)
                    .addOnSuccessListener {
                        val index = subscriptions.indexOf(subscription)
                        subscriptions.remove(subscription)
                        recyclerView.adapter?.notifyItemRemoved(index)
                        updateEmptyState()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.message ?: "Could not cancel subscription", Toast.LENGTH_LONG).show()
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
