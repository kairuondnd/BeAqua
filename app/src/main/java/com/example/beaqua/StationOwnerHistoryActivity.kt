package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StationOwnerHistoryActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var currentStation: User
    private lateinit var rvHistory: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_owner_history)

        drawerLayout = findViewById(R.id.drawerLayoutStationHistory)
        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBackStationHistory)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenuStationHistory)

        btnBack.setOnClickListener { finish() }
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        val username = intent.getStringExtra("USERNAME") ?: ""
        if (username.isEmpty()) {
            Toast.makeText(this, "No station logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        FirebaseHelper.getUser(username).addOnSuccessListener { document ->
            val user = document.toObject(User::class.java)
            if (user == null) {
                Toast.makeText(this, "Station not found", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }
            currentStation = user
            setupSideNav()
            loadHistory()
        }
    }

    private fun setupSideNav() {
        val tvNavName = findViewById<TextView>(R.id.tvNavStationHistoryName)
        if (::currentStation.isInitialized) {
            tvNavName.text = currentStation.name.ifBlank { currentStation.username }
        }

        findViewById<LinearLayout>(R.id.navStationHistoryDashboard).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            finish()
        }

        findViewById<LinearLayout>(R.id.navStationHistoryRevenue).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            val intent = Intent(this, RevenueActivity::class.java)
            intent.putExtra("USERNAME", currentStation.username)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.navStationHistoryFeedbacks).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            val intent = Intent(this, FeedbacksActivity::class.java)
            intent.putExtra("USERNAME", currentStation.username)
            intent.putExtra("ROLE", "Station Owner")
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.navStationHistoryLogout).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHistory() {
        FirebaseHelper.getOrdersForStation(currentStation.username).addOnSuccessListener { result ->
            val allOrders = result.toObjects(Order::class.java)
            val acceptedOrders = allOrders.filter { it.status != "Pending" }

            if (acceptedOrders.isEmpty()) {
                Toast.makeText(this, "No accepted orders found", Toast.LENGTH_SHORT).show()
            }

            rvHistory.adapter = StationOwnerHistoryAdapter(acceptedOrders)
        }
    }
}
