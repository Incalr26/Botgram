package com.incalr26.botgram.data.repository

import android.content.ContentValues
import android.database.Cursor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.incalr26.botgram.data.local.DatabaseHelper
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_CHAT_ID
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_FIRST_NAME
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_LAST_MESSAGE
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_LAST_NAME
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_LAST_TIME
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_TITLE
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_TYPE
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_UNREAD_COUNT
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.COL_USERNAME
import com.incalr26.botgram.data.local.DatabaseHelper.Companion.TABLE_CHATS
import com.incalr26.botgram.data.local.entity.ChatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val dbHelper: DatabaseHelper) {

    private val _allChats = MutableLiveData<List<ChatEntity>>()
    val allChats: LiveData<List<ChatEntity>> = _allChats

    fun refreshChats() {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(TABLE_CHATS, null, null, null, null, null, "$COL_LAST_TIME DESC")
        val chats = mutableListOf<ChatEntity>()
        while (cursor.moveToNext()) {
            chats.add(ChatEntity(
                chatId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHAT_ID)),
                type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                firstName = cursor.getString(cursor.getColumnIndexOrThrow(COL_FIRST_NAME)),
                lastName = cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_NAME)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)),
                lastMessage = cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_MESSAGE)),
                lastTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_TIME)),
                unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_UNREAD_COUNT))
            ))
        }
        cursor.close()
        _allChats.postValue(chats)
    }

    suspend fun getChatById(chatId: Long): ChatEntity? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.query(TABLE_CHATS, null, "$COL_CHAT_ID = ?", arrayOf(chatId.toString()), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                ChatEntity(
                    chatId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHAT_ID)),
                    type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                    firstName = cursor.getString(cursor.getColumnIndexOrThrow(COL_FIRST_NAME)),
                    lastName = cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_NAME)),
                    username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)),
                    lastMessage = cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_MESSAGE)),
                    lastTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_TIME)),
                    unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_UNREAD_COUNT))
                )
            } else null
        }
    }

    suspend fun insertOrUpdateChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // 获取已有未读数
        val existing = getChatById(chat.chatId)
        val newUnread = if (existing != null) existing.unreadCount + 1 else 1
        val finalChat = chat.copy(unreadCount = newUnread)
        val values = ContentValues().apply {
            put(COL_CHAT_ID, finalChat.chatId)
            put(COL_TYPE, finalChat.type)
            put(COL_TITLE, finalChat.title)
            put(COL_FIRST_NAME, finalChat.firstName)
            put(COL_LAST_NAME, finalChat.lastName)
            put(COL_USERNAME, finalChat.username)
            put(COL_LAST_MESSAGE, finalChat.lastMessage)
            put(COL_LAST_TIME, finalChat.lastTime)
            put(COL_UNREAD_COUNT, finalChat.unreadCount)
        }
        db.insertWithOnConflict(TABLE_CHATS, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        refreshChats()
    }

    suspend fun updateUnreadCount(chatId: Long, count: Int) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_UNREAD_COUNT, count)
        }
        db.update(TABLE_CHATS, values, "$COL_CHAT_ID = ?", arrayOf(chatId.toString()))
        refreshChats()
    }
}
