package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FeedbacksActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var currentUsername: String
    private lateinit var role: String
    private val feedbacks = mutableListOf<Feedback>()
    private lateinit var adapter: FeedbackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedbacks)

        currentUsername = intent.getStringExtra("USERNAME") ?: ""
        role = intent.getStringExtra("ROLE") ?: "Station Owner"

        drawerLayout = findViewById(R.id.drawerLayoutFeedbacks)
        val btnBack = findViewById<ImageButton>(R.id.btnBackFeedbacks)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenuFeedbacks)

        btnBack?.setOnClickListener { finish() }
        btnMenu?.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        val tvTitle = findViewById<TextView>(R.id.tvFeedbackTitle)
        tvTitle.text = "Platform Feedbacks"

        val rvFeedbacks = findViewById<RecyclerView>(R.id.rvFeedbacks)
        adapter = FeedbackAdapter(feedbacks)
        rvFeedbacks.layoutManager = LinearLayoutManager(this)
        rvFeedbacks.adapter = adapter

        setupSideNav()
        loadFeedbacks()
    }

    private fun setupSideNav() {
        val navHome = findViewById<LinearLayout>(R.id.navFeedbackHome)
        val navOrders = findViewById<LinearLayout>(R.id.navFeedbackOrders)
        val navInventory = findViewById<LinearLayout>(R.id.navFeedbackInventory)
        val navMessages = findViewById<LinearLayout>(R.id.navFeedbackMessages)
        val navHistory = findViewById<LinearLayout>(R.id.navFeedbackHistory)
        val navProfile = findViewById<LinearLayout>(R.id.navFeedbackProfile)
        val btnLogout = findViewById<Button>(R.id.btnLogoutFeedbacks)

        if (role == "Admin") {
            navHome.setOnClickListener { navigateTo(AdminActivity::class.java) }
            navOrders.setOnClickListener { navigateTo(AdminActivity::class.java, "ORDERS") }
            navInventory.setOnClickListener { navigateTo(AdminActivity::class.java, "INVENTORY") }
            navHistory.visibility = View.GONE
        } else { // Station Owner
            navHome.setOnClickListener { navigateTo(StationOwnerActivity::class.java) }
            navOrders.setOnClickListener { navigateTo(StationOwnerActivity::class.java, "ORDERS") }
            navInventory.setOnClickListener { navigateTo(StationOwnerActivity::class.java, "INVENTORY") }
            navHistory.setOnClickListener { navigateTo(StationOwnerHistoryActivity::class.java) }
        }
        
        navMessages.setOnClickListener {
            val intent = Intent(this, MessagesActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
        }

        navProfile.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun navigateTo(cls: Class<*>, viewType: String? = null) {
        val intent = Intent(this, cls)
        intent.putExtra("USERNAME", currentUsername)
        if (viewType != null) intent.putExtra("VIEW_TYPE", viewType)
        startActivity(intent)
        finish()
    }

    private fun loadFeedbacks() {
        val task = if (role == "Admin") {
            FirebaseHelper.getFeedbacksForStation("", true)
        } else {
            FirebaseHelper.getFeedbacksForStation(currentUsername, false)
        }
        
        task.addOnSuccessListener { result ->
            feedbacks.clear()
            feedbacks.addAll(result.toObjects(Feedback::class.java))
            adapter.notifyDataSetChanged()
        }
    }
}
