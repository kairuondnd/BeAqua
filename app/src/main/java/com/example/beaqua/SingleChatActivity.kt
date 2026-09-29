package com.example.beaqua

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration

class SingleChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var chatPartnerName: TextView
    private lateinit var btnBack: ImageButton

    private lateinit var currentUsername: String
    private lateinit var chatWithUsername: String
    private val messages = mutableListOf<Message>()
    private lateinit var adapter: SingleChatAdapter
    private var messageListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackNavigation.install(this)
        setContentView(R.layout.activity_single_chat)

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        chatPartnerName = findViewById(R.id.tvChatPartnerName)
        btnBack = findViewById(R.id.btnBackChat)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        currentUsername = intent.getStringExtra("CURRENT_USERNAME") ?: return
        chatWithUsername = intent.getStringExtra("CHAT_WITH_USERNAME") ?: return
        if (currentUsername.isBlank() || chatWithUsername.isBlank() || currentUsername == chatWithUsername) {
            finish()
            return
        }

        chatPartnerName.text = if (chatWithUsername == AdminChat.USERNAME) "BeAqua Admin" else chatWithUsername
        if (chatWithUsername != AdminChat.USERNAME) FirebaseHelper.getUser(chatWithUsername).addOnSuccessListener { doc ->
            val partner = doc.toObject(User::class.java)
            chatPartnerName.text = partner?.name ?: chatWithUsername
        }

        adapter = SingleChatAdapter(messages, currentUsername)
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter

        listenForMessages()

        btnSend.setOnClickListener {
            val msgText = etMessage.text.toString().trim()
            if (msgText.isNotEmpty()) {
                btnSend.isEnabled = false
                val message = Message(
                    senderUsername = currentUsername,
                    receiverUsername = chatWithUsername,
                    message = msgText
                )
                FirebaseHelper.sendMessage(message).addOnSuccessListener {
                    if (etMessage.text.toString().trim() == msgText) etMessage.text.clear()
                }.addOnFailureListener {
                    android.widget.Toast.makeText(this, "Message not sent: ${it.message}. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                }.addOnCompleteListener {
                    btnSend.isEnabled = true
                }
            }
        }
    }

    private fun listenForMessages() {
        // Real-time listening for messages
        messageListener = FirebaseHelper.getMessagesBetween(currentUsername, chatWithUsername)
            .addSnapshotListener { snapshot, e ->
                if (snapshot == null) {
                    if (e != null) android.widget.Toast.makeText(this, "Could not load messages: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                
                val fetchedMessages = snapshot.toObjects(Message::class.java)
                messages.clear()
                // Sort locally to avoid Firestore index requirement
                messages.addAll(fetchedMessages.filter {
                    (it.senderUsername == currentUsername && it.receiverUsername == chatWithUsername) ||
                        (it.senderUsername == chatWithUsername && it.receiverUsername == currentUsername)
                }.sortedBy { it.timestamp })
                
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) {
                    rvMessages.smoothScrollToPosition(messages.size - 1)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageListener?.remove()
    }
}
