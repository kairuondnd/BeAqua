package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StationOwnerHistoryActivity : AppCompatActivity() {

    private lateinit var currentStation: User
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnLogoutStationOwner: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_owner_history)

        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)

        btnLogoutStationOwner = findViewById(R.id.btnLogoutStationOwner)
        btnBack = findViewById(R.id.btnBackStationHistory)

        btnBack.setOnClickListener { finish() }

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
            loadHistory()
        }

        btnLogoutStationOwner.setOnClickListener {
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
