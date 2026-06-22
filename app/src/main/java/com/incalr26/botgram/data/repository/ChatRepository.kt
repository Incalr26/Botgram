package com.incalr26.botgram.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.local.DatabaseHelper
import com.incalr26.botgram.data.local.entity.ChatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val dbHelper: DatabaseHelper) {

    private val _allChats = MutableLiveData<List<ChatEntity>>()
    val allChats: LiveData<List<ChatEntity>> = _allChats

    private fun currentHash(): String {
        val token = BotApp.instance.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
            .getString("bot_token", "") ?: ""
        return token.hashCode().toString()
    }

    suspend fun refreshChats() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val hash = currentHash()
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_CHATS, null,
            "${DatabaseHelper.COL_ACCOUNT_HASH} = ?", arrayOf(hash),
            null, null, "${DatabaseHelper.COL_LAST_TIME} DESC"
        )
        val chats = mutableListOf<ChatEntity>()
        while (cursor.moveToNext()) {
            chats.add(chatFromCursor(cursor))
        }
        cursor.close()
        _allChats.postValue(chats)
    }

    suspend fun getAllChatsList(): List<ChatEntity> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val hash = currentHash()
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_CHATS, null,
            "${DatabaseHelper.COL_ACCOUNT_HASH} = ?", arrayOf(hash),
            null, null, "${DatabaseHelper.COL_LAST_TIME} DESC"
        )
        val chats = mutableListOf<ChatEntity>()
        while (cursor.moveToNext()) {
            chats.add(chatFromCursor(cursor))
        }
        cursor.close()
        chats
    }

    suspend fun getChatById(chatId: Long): ChatEntity? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val hash = currentHash()
        db.query(
            DatabaseHelper.TABLE_CHATS, null,
            "${DatabaseHelper.COL_CHAT_ID} = ? AND ${DatabaseHelper.COL_ACCOUNT_HASH} = ?",
            arrayOf(chatId.toString(), hash), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) chatFromCursor(cursor) else null
        }
    }

    suspend fun insertOrUpdateChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val hash = currentHash()
        val existing = getChatById(chat.chatId)
        val newUnread = if (existing != null) existing.unreadCount + 1 else 1
        val finalChat = chat.copy(unreadCount = newUnread, accountHash = hash)
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_CHAT_ID, finalChat.chatId)
            put(DatabaseHelper.COL_TYPE, finalChat.type)
            put(DatabaseHelper.COL_TITLE, finalChat.title)
            put(DatabaseHelper.COL_FIRST_NAME, finalChat.firstName)
            put(DatabaseHelper.COL_LAST_NAME, finalChat.lastName)
            put(DatabaseHelper.COL_USERNAME, finalChat.username)
            put(DatabaseHelper.COL_LAST_MESSAGE, finalChat.lastMessage)
            put(DatabaseHelper.COL_LAST_TIME, finalChat.lastTime)
            put(DatabaseHelper.COL_UNREAD_COUNT, finalChat.unreadCount)
            put(DatabaseHelper.COL_AVATAR_URL, finalChat.avatarUrl)
            put(DatabaseHelper.COL_ACCOUNT_HASH, hash)
        }
        db.insertWithOnConflict(
            DatabaseHelper.TABLE_CHATS, null, values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        refreshChats()
    }

    suspend fun updateUnreadCount(chatId: Long, count: Int) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val hash = currentHash()
        val values = ContentValues().apply { put(DatabaseHelper.COL_UNREAD_COUNT, count) }
        db.update(DatabaseHelper.TABLE_CHATS, values,
            "${DatabaseHelper.COL_CHAT_ID} = ? AND ${DatabaseHelper.COL_ACCOUNT_HASH} = ?",
            arrayOf(chatId.toString(), hash))
        refreshChats()
    }

    suspend fun updateLastMessage(chatId: Long, message: String, time: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val hash = currentHash()
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_LAST_MESSAGE, message)
            put(DatabaseHelper.COL_LAST_TIME, time)
        }
        db.update(DatabaseHelper.TABLE_CHATS, values,
            "${DatabaseHelper.COL_CHAT_ID} = ? AND ${DatabaseHelper.COL_ACCOUNT_HASH} = ?",
            arrayOf(chatId.toString(), hash))
        refreshChats()
    }

    suspend fun updateAvatarUrl(chatId: Long, avatarUrl: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val hash = currentHash()
        val values = ContentValues().apply { put(DatabaseHelper.COL_AVATAR_URL, avatarUrl) }
        db.update(DatabaseHelper.TABLE_CHATS, values,
            "${DatabaseHelper.COL_CHAT_ID} = ? AND ${DatabaseHelper.COL_ACCOUNT_HASH} = ?",
            arrayOf(chatId.toString(), hash))
        refreshChats()
    }

    private fun chatFromCursor(cursor: Cursor): ChatEntity {
        return ChatEntity(
            chatId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_ID)),
            type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TYPE)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE)),
            firstName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FIRST_NAME)),
            lastName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LAST_NAME)),
            username = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USERNAME)),
            lastMessage = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LAST_MESSAGE)),
            lastTime = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LAST_TIME)),
            unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNREAD_COUNT)),
            avatarUrl = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_AVATAR_URL)),
            accountHash = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ACCOUNT_HASH))
        )
    }
}
