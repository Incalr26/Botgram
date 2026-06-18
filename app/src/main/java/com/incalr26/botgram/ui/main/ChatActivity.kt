package com.incalr26.botgram.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import com.incalr26.botgram.data.repository.MessageRepository
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ChatActivity : AppCompatActivity() {
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private var chatId: Long = 0

    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        gotoCrash(throwable)
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getLongExtra("chatId", 0) == chatId) {
                loadMessages()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_chat)

            chatId = intent.getLongExtra("chatId", 0)
            if (chatId == 0L) {
                finish()
                return
            }

            val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "聊天"
            supportActionBar?.subtitle = null

            val app = BotApp.instance ?: throw IllegalStateException("应用未初始化")

            chatRepository = ChatRepository(app.databaseHelper)
            messageRepository = MessageRepository(app.databaseHelper)
            adapter = MessageAdapter()

            recyclerView = findViewById(R.id.messagesRecyclerView)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(this).apply {
                stackFromEnd = true
            }

            val messageInput = findViewById<EditText>(R.id.messageInput)
            val sendButton = findViewById<MaterialButton>(R.id.sendButton)

            sendButton.setOnClickListener {
                val text = messageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendTextMessage(text)
                    messageInput.text.clear()
                }
            }

            lifecycleScope.launch(Dispatchers.IO + crashHandler) {
                loadMessagesInternal()
                loadTitleAndType()
                chatRepository.updateUnreadCount(chatId, 0)
            }

            ContextCompat.registerReceiver(
                this,
                messageReceiver,
                IntentFilter("com.incalr26.botgram.NEW_MESSAGE"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

        } catch (e: Exception) {
            gotoCrash(e)
        }
    }

    private suspend fun loadTitleAndType() {
        val chat = chatRepository.getChatById(chatId)
        withContext(Dispatchers.Main) {
            if (chat != null) {
                val name = if (chat.type == "private") {
                    chat.firstName ?: chat.username ?: "私聊"
                } else {
                    chat.title ?: "群组"
                }
                supportActionBar?.title = name
                val typeStr = when (chat.type) {
                    "private" -> "私聊"
                    "group" -> "群组"
                    "supergroup" -> "超级群组"
                    "channel" -> "频道"
                    else -> chat.type
                }
                supportActionBar?.subtitle = typeStr
            }
        }
    }

    private suspend fun loadMessagesInternal() {
        val messages = messageRepository.getMessages(chatId)
        withContext(Dispatchers.Main) {
            adapter.submitList(messages)
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            loadMessagesInternal()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(messageReceiver)
        } catch (_: Exception) {}
    }

    private fun gotoCrash(throwable: Throwable) {
        val intent = Intent(this, CrashActivity::class.java).apply {
            putExtra("stack_trace", android.util.Log.getStackTraceString(throwable))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun sendTextMessage(text: String) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
                .getString("bot_token", "") ?: error("未登录")
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val jsonBody = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            val response = ApiClient.getClient().newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "发送失败", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val msg = JSONObject(response.body?.string() ?: "")
            if (msg.getBoolean("ok")) {
                val result = msg.getJSONObject("result")
                val messageEntity = MessageEntity(
                    messageId = result.getLong("message_id"),
                    chatId = chatId,
                    senderUserId = null,
                    senderName = "我",
                    text = text,
                    date = result.getLong("date"),
                    isOutgoing = true,
                    rawJson = result.toString()
                )
                messageRepository.insertMessage(messageEntity)
                chatRepository.updateLastMessage(chatId, text, result.getLong("date") * 1000)
                loadMessagesInternal()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
