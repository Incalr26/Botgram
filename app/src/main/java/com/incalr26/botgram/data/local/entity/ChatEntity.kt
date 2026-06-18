package com.incalr26.botgram.data.local.entity

data class ChatEntity(
    val chatId: Long,
    val type: String,
    val title: String?,
    val firstName: String?,
    val lastName: String?,
    val username: String?,
    val lastMessage: String?,
    val lastTime: Long,
    val unreadCount: Int = 0
)
