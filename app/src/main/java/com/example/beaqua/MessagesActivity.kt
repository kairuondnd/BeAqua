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
        BackNavigation.install(this)
        setContentView(R.layout.activity_messages)

        rvChats = findViewById(R.id.rvChats)
        rvChats.layoutManager = LinearLayoutManager(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBackMessages)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val username = intent.getStringExtra("USERNAME") ?: return
        if (username == AdminChat.USERNAME) {
            currentUser = AdminChat.contact()
            findViewById<android.widget.TextView>(R.id.tvMessagesTitle).text = "Station messages"
            FirebaseHelper.getStationOwners().addOnSuccessListener { snapshot ->
                showChats(snapshot.toObjects(User::class.java).sortedBy { it.name.lowercase() })
            }.addOnFailureListener {
                android.widget.Toast.makeText(this, "Could not load stations: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        
        FirebaseHelper.getUser(username).addOnSuccessListener { document ->
            currentUser = document.toObject(User::class.java) ?: return@addOnSuccessListener
            loadChats()
        }
    }

    private fun loadChats() {
        val contacts = if (currentUser.accountType == "Station Owner") listOf(AdminChat.contact()) else emptyList()
        showChats(contacts)
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

            val chatUsers = contacts.toMutableList()
            var loadedCount = 0
            
            if (chatUsernames.isEmpty()) {
                showChats(chatUsers)
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

                FirebaseHelper.getUser(targetUname).addOnCompleteListener { task ->
                    if (task.isSuccessful) task.result.toObject(User::class.java)?.let { chatUsers.add(it) }
                    loadedCount++
                    if (loadedCount == chatUsernames.size) {
                        showChats(chatUsers)
                    }
                }
            }
        }.addOnFailureListener {
            android.widget.Toast.makeText(this, "Could not load other chats. Please try again.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun showChats(users: List<User>) {
        rvChats.adapter = ChatListAdapter(users.distinctBy { it.username }) { user ->
            AdminChat.open(this, currentUser.username, user.username)
        }
    }
}
