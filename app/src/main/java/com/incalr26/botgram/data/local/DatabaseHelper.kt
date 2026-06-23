package com.incalr26.botgram.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "botgram.db"
        const val DATABASE_VERSION = 5

        const val TABLE_CHATS = "chats"
        const val TABLE_MESSAGES = "messages"

        const val COL_CHAT_ID = "chatId"
        const val COL_TYPE = "type"
        const val COL_TITLE = "title"
        const val COL_FIRST_NAME = "firstName"
        const val COL_LAST_NAME = "lastName"
        const val COL_USERNAME = "username"
        const val COL_LAST_MESSAGE = "lastMessage"
        const val COL_LAST_TIME = "lastTime"
        const val COL_UNREAD_COUNT = "unreadCount"
        const val COL_AVATAR_URL = "avatarUrl"
        const val COL_ACCOUNT_HASH = "accountHash"

        const val COL_MESSAGE_ID = "messageId"
        const val COL_SENDER_USER_ID = "senderUserId"
        const val COL_SENDER_NAME = "senderName"
        const val COL_TEXT = "text"
        const val COL_DATE = "date"
        const val COL_IS_OUTGOING = "isOutgoing"
        const val COL_RAW_JSON = "rawJson"
        const val COL_ENTITIES = "entities"
        const val COL_REPLY_TO_JSON = "replyToJson"
        const val COL_SENDER_ROLE = "senderRole"
        const val COL_SENDER_TITLE = "senderTitle"
        const val COL_IS_DELETED = "isDeleted"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createChatsTable = """
            CREATE TABLE $TABLE_CHATS (
                $COL_CHAT_ID INTEGER PRIMARY KEY,
                $COL_TYPE TEXT NOT NULL,
                $COL_TITLE TEXT,
                $COL_FIRST_NAME TEXT,
                $COL_LAST_NAME TEXT,
                $COL_USERNAME TEXT,
                $COL_LAST_MESSAGE TEXT,
                $COL_LAST_TIME INTEGER NOT NULL DEFAULT 0,
                $COL_UNREAD_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_AVATAR_URL TEXT,
                $COL_ACCOUNT_HASH TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent()

        val createMessagesTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MESSAGE_ID INTEGER,
                $COL_CHAT_ID INTEGER NOT NULL,
                $COL_SENDER_USER_ID INTEGER,
                $COL_SENDER_NAME TEXT,
                $COL_TEXT TEXT,
                $COL_DATE INTEGER NOT NULL,
                $COL_IS_OUTGOING INTEGER NOT NULL DEFAULT 0,
                $COL_RAW_JSON TEXT,
                $COL_ENTITIES TEXT,
                $COL_REPLY_TO_JSON TEXT,
                $COL_SENDER_ROLE TEXT,
                $COL_SENDER_TITLE TEXT,
                $COL_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY ($COL_MESSAGE_ID, $COL_CHAT_ID)
            )
        """.trimIndent()

        db.execSQL(createChatsTable)
        db.execSQL(createMessagesTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_CHATS ADD COLUMN $COL_AVATAR_URL TEXT")
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_ENTITIES TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_CHATS ADD COLUMN $COL_ACCOUNT_HASH TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE $TABLE_CHATS SET $COL_ACCOUNT_HASH = 'legacy'")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_REPLY_TO_JSON TEXT")
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_SENDER_ROLE TEXT")
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_SENDER_TITLE TEXT")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_IS_DELETED INTEGER NOT NULL DEFAULT 0")
        }
    }
}
