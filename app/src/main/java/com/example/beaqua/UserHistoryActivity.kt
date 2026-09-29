package com.example.beaqua

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class UserHistoryActivity : AppCompatActivity() {

    private var currentUser: User? = null
    private lateinit var rvHistory: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var tvEmptyMessage: TextView
    private lateinit var tabLayout: TabLayout
    private var historyListener: ListenerRegistration? = null
    private lateinit var adapter: UserHistoryAdapter
    private val fullHistoryList = mutableListOf<Order>()
    private val filteredList = mutableListOf<Order>()
    private var role: String = "User"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackNavigation.install(this)
        setContentView(R.layout.activity_user_history)

        val username = intent.getStringExtra("USERNAME") ?: ""
        role = intent.getStringExtra("ROLE") ?: "User"
        val startTab = intent.getIntExtra("SELECT_TAB", 0)
        
        // Initialize UI
        val btnBack = findViewById<MaterialCardView>(R.id.btnBackHistory)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val tvTitle = findViewById<TextView>(R.id.tvHistoryTitle)
        tabLayout = findViewById(R.id.tabLayoutHistory)
        
        tvTitle.text = "Order History"

        rvHistory = findViewById(R.id.rvHistory)
        layoutEmpty = findViewById(R.id.layoutEmptyHistory)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
        
        // Initialize adapter with an empty list explicitly
        adapter = UserHistoryAdapter(emptyList(), role) { order ->
            showFeedbackDialog(order)
        }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        setupTabLayout()

        if (username.isNotEmpty()) {
            FirebaseHelper.getUser(username).addOnSuccessListener { document ->
                currentUser = document.toObject(User::class.java)
                startListeningForHistory()
                
                // Select starting tab if provided
                if (startTab != 0 && startTab < tabLayout.tabCount) {
                    tabLayout.getTabAt(startTab)?.select()
                }
            }
        }
        
        // Ensure UI is initialized to empty state
        updateUI()
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterOrders(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun startListeningForHistory() {
        currentUser?.let { user ->
            val query = FirebaseHelper.ordersCollection.whereEqualTo("customerName", user.username)

            historyListener = query
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        fetchHistoryFallback(user.username)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        fullHistoryList.clear()
                        for (doc in snapshots.documents) {
                            val order = doc.toObject(Order::class.java)
                            if (order != null) {
                                order.id = doc.id
                                fullHistoryList.add(order)
                            }
                        }
                        // Refresh data for current tab
                        filterOrders(tabLayout.selectedTabPosition)
                    }
                }
        }
    }

    private fun fetchHistoryFallback(username: String) {
        val query = FirebaseHelper.ordersCollection.whereEqualTo("customerName", username)

        query.get()
            .addOnSuccessListener { snapshots ->
                fullHistoryList.clear()
                for (doc in snapshots.documents) {
                    val order = doc.toObject(Order::class.java)
                    if (order != null) {
                        order.id = doc.id
                        fullHistoryList.add(order)
                    }
                }
                fullHistoryList.sortByDescending { it.timestamp }
                filterOrders(tabLayout.selectedTabPosition)
            }
    }

    private fun filterOrders(tabPosition: Int) {
        val selectedStatus = when (tabPosition) {
            1 -> "Pending"
            2 -> "Accepted"
            3 -> "Delivered"
            4 -> "Rejected"
            else -> null
        }
        // Keep the entire checkout visible, including when older items have mixed statuses.
        val newFilteredList = groupOrderHistory(fullHistoryList)
            .filter { group -> selectedStatus == null || group.items.any { it.status == selectedStatus } }
            .flatMap { it.items }

        filteredList.clear()
        filteredList.addAll(newFilteredList)
        
        val categoryName = tabLayout.getTabAt(tabPosition)?.text ?: ""
        tvEmptyMessage.text = "You don't have any $categoryName orders yet."
        
        updateUI()
    }

    private fun updateUI() {
        // Update adapter regardless of visibility
        adapter.updateData(filteredList)
        
        if (filteredList.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE
            // Use post to ensure scheduleLayoutAnimation happens after layout pass
            rvHistory.post {
                rvHistory.scheduleLayoutAnimation()
            }
        }
    }

    private fun showFeedbackDialog(orders: List<Order>) {
        val order = orders.first()
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_feedback, null)
        dialog.setContentView(android.widget.ScrollView(this).apply { addView(view) })

        val tvStationName = view.findViewById<TextView>(R.id.tvStationNameFeedback)
        val ratingStation = view.findViewById<RatingBar>(R.id.ratingStation)
        val ratingProduct = view.findViewById<RatingBar>(R.id.ratingProductQuality)
        val etComment = view.findViewById<EditText>(R.id.etFeedbackComment)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitFeedback)

        tvStationName.text = "${order.stationName.ifEmpty { order.stationOwnerUsername }}\n" +
            orders.joinToString("\n") { "${it.productName} (Qty: ${it.quantity})" }

        btnSubmit.setOnClickListener {
            val stationRating = ratingStation.rating
            val productRating = ratingProduct.rating
            val comment = etComment.text.toString().trim()

            if (stationRating == 0f || productRating == 0f) {
                Toast.makeText(this, "Please rate both product quality and service quality", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val feedback = Feedback(
                orderId = order.id,
                customerUsername = order.customerName,
                stationOwnerUsername = order.stationOwnerUsername,
                stationRating = (stationRating + productRating) / 2f,
                productQualityRating = productRating,
                serviceQualityRating = stationRating,
                productName = order.productName,
                remarks = comment,
                timestamp = System.currentTimeMillis()
            )

            btnSubmit.isEnabled = false
            btnSubmit.text = "Submitting..."
            FirebaseHelper.addFeedback(feedback, orders.map { it.id }).addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { error ->
                btnSubmit.isEnabled = true
                btnSubmit.text = "Submit feedback"
                Toast.makeText(this, error.message ?: "Failed to submit feedback", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        historyListener?.remove()
    }
}
