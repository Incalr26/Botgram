package com.incalr26.botgram.data.local.entity

data class MessageEntity(
    val messageId: Long,
    val chatId: Long,
    val senderUserId: Long?,
    val senderName: String?,
    val text: String?,
    val date: Long,
    val isOutgoing: Boolean = false,
    val rawJson: String? = null,
    val entities: String? = null,
    val replyToJson: String? = null   // 引用的原始消息 JSON
)
