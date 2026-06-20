package com.incalr26.botgram.ui.main

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

            // 设置状态栏占位高度
            val statusBarPlaceholder = findViewById<View>(R.id.statusBarPlaceholder)
            val statusBarHeight = getStatusBarHeight()
            statusBarPlaceholder.layoutParams.height = statusBarHeight

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

            // 长按回调
            adapter = MessageAdapter(
                onLongClick = { message, view ->
                    showMessageMenu(message, view)
                    true
                }
            )

            recyclerView = findViewById(R.id.messagesRecyclerView)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(this).apply {
                stackFromEnd = true
            }

            val messageInput = findViewById<EditText>(R.id.messageInput)
            val sendButton = findViewById<ImageButton>(R.id.sendButton)

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

    // 长按菜单
    private fun showMessageMenu(message: MessageEntity, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("复制")
        popup.menu.add("复读 (+1)")
        if (message.isOutgoing) {
            popup.menu.add("撤回")
        }
        // 复制链接、编辑、回复、转发等以后补充
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "复制" -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text ?: ""))
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                }
                "复读 (+1)" -> {
                    messageInputSetText(message.text ?: "")
                }
                "撤回" -> {
                    lifecycleScope.launch(Dispatchers.IO + crashHandler) {
                        deleteMessage(message.messageId)
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun messageInputSetText(text: String) {
        findViewById<EditText>(R.id.messageInput).setText(text)
    }

    private suspend fun deleteMessage(messageId: Long) {
        try {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return
            val url = "https://api.telegram.org/bot$token/deleteMessage?chat_id=$chatId&message_id=$messageId"
            val request = Request.Builder().url(url).build()
            val response = ApiClient.getClient().newCall(request).execute()
            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "消息已撤回", Toast.LENGTH_SHORT).show()
                    loadMessages()
                }
            } else {
                val body = response.body?.string() ?: ""
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "撤回失败: $body", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ChatActivity, "撤回失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 其余方法保持之前的实现（loadTitleAndType, loadMessagesInternal, sendTextMessage 等）
    // 由于篇幅，此处省略，实际使用时请保留之前完整的这些方法
    // 参见上几轮的完整 ChatActivity

    private suspend fun loadTitleAndType() {
        val chat = chatRepository.getChatById(chatId)
        var memberCount: Int? = null
        try {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: ""
            val url = "https://api.telegram.org/bot$token/getChat?chat_id=$chatId"
            val request = Request.Builder().url(url).build()
            val response = ApiClient.getClient().newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                if (json.getBoolean("ok")) {
                    val chatObj = json.getJSONObject("result")
                    memberCount = if (chatObj.has("member_count")) chatObj.getInt("member_count") else null
                }
            }
        } catch (_: Exception) {}

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
                val memberStr = if (memberCount != null) " · ${memberCount}人" else ""
                supportActionBar?.subtitle = typeStr + memberStr
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
        try { unregisterReceiver(messageReceiver) } catch (_: Exception) {}
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
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val jsonBody = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
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

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
