package com.incalr26.botgram.ui.main

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        try {
            setContentView(R.layout.activity_chat)

            val statusBarPlaceholder = findViewById<View>(R.id.statusBarPlaceholder)
            statusBarPlaceholder.layoutParams.height = getStatusBarHeight()

            val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "聊天"
            supportActionBar?.subtitle = null

            chatId = intent.getLongExtra("chatId", 0)
            if (chatId == 0L) {
                finish()
                return
            }

            val app = BotApp.instance ?: throw IllegalStateException("应用未初始化")

            chatRepository = ChatRepository(app.databaseHelper)
            messageRepository = MessageRepository(app.databaseHelper)

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

    private fun showMessageMenu(message: MessageEntity, anchor: View) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@ChatActivity, R.drawable.popup_menu_background)
            elevation = 12f * resources.displayMetrics.density
            clipToOutline = true
        }

        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val textColor = typedValue.data
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data

        val items = mutableListOf(
            Triple("复制", R.drawable.ic_copy, 1),
            Triple("复读", R.drawable.ic_plus_one_outline, 2)
        )
        if (message.isOutgoing) {
            items.add(Triple("撤回", R.drawable.ic_revoke, 3))
        }

        val popupWindow = PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ContextCompat.getDrawable(this@ChatActivity, android.R.color.transparent))
        }

        items.forEach { (title, iconRes, action) ->
            val itemView = layoutInflater.inflate(R.layout.item_popup_menu, container, false)
            val icon = itemView.findViewById<ImageView>(R.id.menu_icon)
            val text = itemView.findViewById<TextView>(R.id.menu_text)
            icon.setImageResource(iconRes)
            icon.setColorFilter(primaryColor)
            text.text = title
            text.setTextColor(textColor)
            itemView.setOnClickListener {
                handleMenuAction(action, message)
                popupWindow.dismiss()
            }
            container.addView(itemView)
        }

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = container.measuredHeight
        val popupWidth = container.measuredWidth

        val bubble = anchor.findViewById<View>(R.id.messageText)
        val bubbleLocation = IntArray(2)
        bubble?.getLocationOnScreen(bubbleLocation) ?: anchor.getLocationOnScreen(bubbleLocation)
        val bubbleLeft = bubbleLocation[0]

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val xOff = bubbleLeft - anchorLocation[0]

        val screenHeight = resources.displayMetrics.heightPixels
        val bubbleBottom = bubbleLocation[1] + (bubble?.height ?: anchor.height)
        val yOff = if (bubbleBottom + popupHeight > screenHeight) -popupHeight - (bubble?.height ?: anchor.height) else 0

        popupWindow.showAsDropDown(anchor, xOff, yOff, Gravity.START or Gravity.TOP)
    }

    private fun handleMenuAction(action: Int, message: MessageEntity) {
        when (action) {
            1 -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text ?: ""))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
            2 -> handleRepeat(message.text ?: "")
            3 -> lifecycleScope.launch(Dispatchers.IO + crashHandler) { deleteMessage(message.messageId) }
        }
    }

    private fun handleRepeat(text: String) {
        val prefs = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
        val needConfirm = prefs.getBoolean("repeat_confirm", true)
        if (needConfirm) {
            MaterialAlertDialogBuilder(this)
                .setTitle("复读确认")
                .setMessage("确定要复读这条消息吗？")
                .setPositiveButton("确定") { _, _ -> sendTextMessage(text) }
                .setNegativeButton("取消", null)
                .show()
        } else {
            sendTextMessage(text)
        }
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
