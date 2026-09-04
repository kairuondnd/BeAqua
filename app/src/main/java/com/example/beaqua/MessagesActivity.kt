package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MessagesActivity : AppCompatActivity() {

    private lateinit var rvChats: RecyclerView
    private lateinit var currentUser: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        rvChats = findViewById(R.id.rvChats)
        rvChats.layoutManager = LinearLayoutManager(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBackMessages)
        btnBack.setOnClickListener { finish() }

        val username = intent.getStringExtra("USERNAME") ?: return
        
        FirebaseHelper.getUser(username).addOnSuccessListener { document ->
            currentUser = document.toObject(User::class.java) ?: return@addOnSuccessListener
            loadChats()
        }
    }

    private fun loadChats() {
        val chatTask = if (currentUser.accountType == "Station Owner") {
            FirebaseHelper.getChatUsersForStation(currentUser.username)
        } else {
            FirebaseHelper.getChatUsersForUser(currentUser.username)
        }

        chatTask.addOnSuccessListener { result ->
            val orders = result.toObjects(Order::class.java)
            val chatUsernames = if (currentUser.accountType == "Station Owner") {
                orders.map { it.customerName }.distinct()
            } else {
                orders.map { it.stationName }.distinct()
            }

            val chatUsers = mutableListOf<User>()
            var loadedCount = 0
            
            if (chatUsernames.isEmpty()) {
                rvChats.adapter = ChatListAdapter(emptyList()) {}
                return@addOnSuccessListener
            }

            for (uname in chatUsernames) {
                // If it's a station name, we need to find the owner's username. 
                // But in this system, stationName is usually stored along with stationOwnerUsername.
                // FirebaseHelper.getChatUsersForUser returns orders.
                
                val targetUname = if (currentUser.accountType == "Station Owner") {
                    uname
                } else {
                    // For customers, the distinct list was stationNames, but we need the owner username to fetch User object
                    orders.find { it.stationName == uname }?.stationOwnerUsername ?: uname
                }

                FirebaseHelper.getUser(targetUname).addOnSuccessListener { doc ->
                    doc.toObject(User::class.java)?.let { chatUsers.add(it) }
                    loadedCount++
                    if (loadedCount == chatUsernames.size) {
                        rvChats.adapter = ChatListAdapter(chatUsers) { user ->
                            val intent = Intent(this, SingleChatActivity::class.java)
                            intent.putExtra("CURRENT_USERNAME", currentUser.username)
                            intent.putExtra("CHAT_WITH_USERNAME", user.username)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}
