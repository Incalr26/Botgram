package com.incalr26.botgram.data.repository

import android.content.ContentValues
import com.incalr26.botgram.data.local.DatabaseHelper
import com.incalr26.botgram.data.local.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageRepository(private val dbHelper: DatabaseHelper) {

    suspend fun getMessages(chatId: Long): List<MessageEntity> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_MESSAGES, null,
            "${DatabaseHelper.COL_CHAT_ID} = ?", arrayOf(chatId.toString()),
            null, null, "${DatabaseHelper.COL_DATE} ASC"
        )
        val messages = mutableListOf<MessageEntity>()
        while (cursor.moveToNext()) {
            messages.add(MessageEntity(
                messageId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MESSAGE_ID)),
                chatId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_ID)),
                senderUserId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_USER_ID)),
                senderName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_NAME)),
                text = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TEXT)),
                date = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DATE)),
                isOutgoing = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_OUTGOING)) == 1,
                rawJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RAW_JSON)),
                entities = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ENTITIES)),
                replyToJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REPLY_TO_JSON)),
                senderRole = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_ROLE)),
                senderTitle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_TITLE)),
                isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_DELETED)) == 1,
                isEdited = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_EDITED)) == 1,
                editHistory = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EDIT_HISTORY)),
                reactions = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REACTIONS))
            ))
        }
        cursor.close()
        messages
    }

    suspend fun getMessage(chatId: Long, messageId: Long): MessageEntity? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_MESSAGES, null,
            "${DatabaseHelper.COL_CHAT_ID} = ? AND ${DatabaseHelper.COL_MESSAGE_ID} = ?",
            arrayOf(chatId.toString(), messageId.toString()),
            null, null, null
        )
        var message: MessageEntity? = null
        if (cursor.moveToFirst()) {
            message = MessageEntity(
                messageId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MESSAGE_ID)),
                chatId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_ID)),
                senderUserId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_USER_ID)),
                senderName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_NAME)),
                text = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TEXT)),
                date = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DATE)),
                isOutgoing = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_OUTGOING)) == 1,
                rawJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RAW_JSON)),
                entities = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ENTITIES)),
                replyToJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REPLY_TO_JSON)),
                senderRole = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_ROLE)),
                senderTitle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SENDER_TITLE)),
                isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_DELETED)) == 1,
                isEdited = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_EDITED)) == 1,
                editHistory = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EDIT_HISTORY)),
                reactions = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REACTIONS))
            )
        }
        cursor.close()
        message
    }

    suspend fun insertMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_MESSAGE_ID, message.messageId)
            put(DatabaseHelper.COL_CHAT_ID, message.chatId)
            put(DatabaseHelper.COL_SENDER_USER_ID, message.senderUserId)
            put(DatabaseHelper.COL_SENDER_NAME, message.senderName)
            put(DatabaseHelper.COL_TEXT, message.text)
            put(DatabaseHelper.COL_DATE, message.date)
            put(DatabaseHelper.COL_IS_OUTGOING, if (message.isOutgoing) 1 else 0)
            put(DatabaseHelper.COL_RAW_JSON, message.rawJson)
            put(DatabaseHelper.COL_ENTITIES, message.entities)
            put(DatabaseHelper.COL_REPLY_TO_JSON, message.replyToJson)
            put(DatabaseHelper.COL_SENDER_ROLE, message.senderRole)
            put(DatabaseHelper.COL_SENDER_TITLE, message.senderTitle)
            put(DatabaseHelper.COL_IS_DELETED, if (message.isDeleted) 1 else 0)
            put(DatabaseHelper.COL_IS_EDITED, if (message.isEdited) 1 else 0)
            put(DatabaseHelper.COL_EDIT_HISTORY, message.editHistory)
            put(DatabaseHelper.COL_REACTIONS, message.reactions)
        }
        db.insertWithOnConflict(DatabaseHelper.TABLE_MESSAGES, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun markMessageAsDeleted(messageId: Long, chatId: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put(DatabaseHelper.COL_IS_DELETED, 1) }
        db.update(
            DatabaseHelper.TABLE_MESSAGES, values,
            "${DatabaseHelper.COL_MESSAGE_ID} = ? AND ${DatabaseHelper.COL_CHAT_ID} = ?",
            arrayOf(messageId.toString(), chatId.toString())
        )
    }
}
