package com.example.beaqua

data class Message(
    var id: String = "",
    var senderUsername: String = "",
    var receiverUsername: String = "",
    var message: String = "",
    var timestamp: Long = 0L,
    var chatId: String = ""
)
