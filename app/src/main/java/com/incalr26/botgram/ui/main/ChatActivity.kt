package com.incalr26.botgram.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import com.incalr26.botgram.data.repository.MessageRepository
import com.incalr26.botgram.util.FileHelper
import com.incalr26.botgram.util.NewMessageNotifier
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
    private var replyToMessageId: Long? = null
    private var chatType: String = "private"

    private val crashHandler = CoroutineExceptionHandler { _, throwable -> gotoCrash(throwable) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            setContentView(R.layout.activity_chat)

            findViewById<View>(R.id.statusBarPlaceholder).layoutParams.height = getStatusBarHeight()

            val rootView = findViewById<View>(android.R.id.content)
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                val imeBars = insets.getInsets(WindowInsetsCompat.Type.ime())
                val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val bottomBar = findViewById<LinearLayout>(R.id.bottomBar)
                val bottomPadding = if (imeBars.bottom > 0) imeBars.bottom else navBars.bottom
                bottomBar.setPadding(bottomBar.paddingLeft, bottomBar.paddingTop, bottomBar.paddingRight, bottomPadding)
                insets
            }

            val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "聊天"

            chatId = intent.getLongExtra("chatId", 0)
            if (chatId == 0L) { finish(); return }

            val app = BotApp.instance
            chatRepository = ChatRepository(app.databaseHelper)
            messageRepository = MessageRepository(app.databaseHelper)

            adapter = MessageAdapter(
                onLongClick = { message, view ->
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (imm.isActive && currentFocus != null) imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
                    view.postDelayed({ if (!isFinishing && !isDestroyed) showMessageMenu(message, view) }, 150)
                    true
                }
            )

            recyclerView = findViewById(R.id.messagesRecyclerView)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

            val messageInput = findViewById<EditText>(R.id.messageInput)
            val sendButton = findViewById<ImageButton>(R.id.sendButton)
            val attachButton = findViewById<ImageButton>(R.id.attachButton)
            val replyBanner = findViewById<LinearLayout>(R.id.replyBanner)

            messageInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val hasText = s?.toString()?.trim()?.isNotEmpty() == true
                    sendButton.alpha = if (hasText) 1.0f else 0.4f
                    sendButton.isEnabled = hasText
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            sendButton.isEnabled = false // 初始禁用

            attachButton.setOnClickListener { showAttachMenu() }

            findViewById<ImageButton>(R.id.cancelReply).setOnClickListener {
                replyToMessageId = null
                replyBanner.visibility = View.GONE
            }

            sendButton.setOnClickListener {
                val text = messageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendTextMessage(text, replyToMessageId)
                    replyToMessageId = null
                    replyBanner.visibility = View.GONE
                    messageInput.text.clear()
                }
            }

            lifecycleScope.launch(Dispatchers.IO + crashHandler) {
                loadMessagesInternal()
                loadTitleAndType()
                chatRepository.updateUnreadCount(chatId, 0)
            }

            NewMessageNotifier.newMessage.observe(this) {
                lifecycleScope.launch(Dispatchers.IO) { delay(100); loadMessagesInternal() }
            }

        } catch (e: Exception) { gotoCrash(e) }
    }

    private fun showAttachMenu() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 32)
        }

        val items = listOf(
            Pair("图片 (相册)", android.R.drawable.ic_menu_gallery),
            Pair("视频", android.R.drawable.presence_video_online),
            Pair("文件", android.R.drawable.ic_menu_agenda)
        )

        items.forEach { (title, iconRes) ->
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(48, 36, 48, 36)
                background = ContextCompat.getDrawable(this@ChatActivity, androidx.appcompat.R.drawable.abc_item_background_holo_light)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    Toast.makeText(this@ChatActivity, "底层文件选择与分发接口开发中...", Toast.LENGTH_SHORT).show()
                    bottomSheetDialog.dismiss()
                }
            }
            val icon = ImageView(this).apply { setImageResource(iconRes) }
            val text = TextView(this).apply {
                this.text = title
                textSize = 16f
                setPadding(32, 0, 0, 0)
            }
            layout.addView(icon)
            layout.addView(text)
            container.addView(layout)
        }
        
        bottomSheetDialog.setContentView(container)
        bottomSheetDialog.show()
    }

    private fun setReplyBanner(message: MessageEntity) {
        val banner = findViewById<LinearLayout>(R.id.replyBanner)
        findViewById<TextView>(R.id.replyText).text = "回复 ${message.senderName ?: "未知"}: ${message.text?.take(50) ?: ""}"
        banner.visibility = View.VISIBLE
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private suspend fun loadTitleAndType() {
        val chat = chatRepository.getChatById(chatId)
        var memberCount: Int? = null
        try {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: ""
            val res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/getChat?chat_id=$chatId").build()).execute()
            if (res.isSuccessful) {
                val json = JSONObject(res.body?.string() ?: "")
                if (json.getBoolean("ok")) memberCount = json.getJSONObject("result").optInt("member_count")
            }
        } catch (_: Exception) {}

        withContext(Dispatchers.Main) {
            if (chat != null) {
                chatType = chat.type
                supportActionBar?.title = if (chat.type == "private") chat.firstName ?: chat.username ?: "私聊" else chat.title ?: "群组"
                val typeStr = when (chat.type) { "private" -> "私聊"; "group" -> "群组"; "supergroup" -> "超级群组"; "channel" -> "频道"; else -> chat.type }
                supportActionBar?.subtitle = typeStr + (if (memberCount != null && memberCount > 0) " · ${memberCount}人" else "")
            }
        }
    }

    private suspend fun loadMessagesInternal() {
        val messages = messageRepository.getMessages(chatId)
        withContext(Dispatchers.Main) {
            val list = messages.toList()
            adapter.submitList(list) {
                if (list.isNotEmpty()) {
                    recyclerView.post { recyclerView.scrollToPosition(list.size - 1) }
                }
            }
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
            Triple("复读", R.drawable.ic_plus_one_outline, 2),
            Triple("转发式复读", R.drawable.ic_repeat, 6),
            Triple("转发给...", R.drawable.ic_send, 7),
            Triple("回复", R.drawable.ic_reply, 4)
        )
        if (chatType != "private") items.add(2, Triple("复制链接", R.drawable.ic_link, 5))
        if (message.isOutgoing) items.add(Triple("撤回", R.drawable.ic_revoke, 3))

        val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (e: Exception) { JSONObject() }
        if (rawObj.has("photo") || rawObj.has("sticker") || rawObj.has("video") || rawObj.has("document")) {
            items.add(0, Triple("保存", R.drawable.ic_save_media, 8))
        }

        val popupWindow = PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ContextCompat.getDrawable(this@ChatActivity, android.R.color.transparent))
        }

        items.forEach { (title, iconRes, action) ->
            val itemView = layoutInflater.inflate(R.layout.item_popup_menu, container, false)
            itemView.findViewById<ImageView>(R.id.menu_icon).apply { setImageResource(iconRes); setColorFilter(primaryColor) }
            itemView.findViewById<TextView>(R.id.menu_text).apply { text = title; setTextColor(textColor) }
            itemView.setOnClickListener { handleMenuAction(action, message); popupWindow.dismiss() }
            container.addView(itemView)
        }

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = container.measuredHeight
        val bubble = anchor.findViewById<View>(R.id.messageText) ?: anchor
        val bubbleLocation = IntArray(2)
        bubble.getLocationOnScreen(bubbleLocation)
        val xOff = bubbleLocation[0] - IntArray(2).apply { anchor.getLocationOnScreen(this) }[0]
        var yOff = 0
        if (bubbleLocation[1] + bubble.height + popupHeight > resources.displayMetrics.heightPixels) {
            yOff = bubbleLocation[1] - popupHeight - (IntArray(2).apply { anchor.getLocationOnScreen(this) }[1] + anchor.height)
        }
        popupWindow.showAsDropDown(anchor, xOff, yOff, Gravity.START or Gravity.TOP)
    }

    private fun handleMenuAction(action: Int, message: MessageEntity) {
        when (action) {
            1 -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text ?: ""))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
            2 -> handleRepeatAction(message, false)
            3 -> lifecycleScope.launch(Dispatchers.IO + crashHandler) { deleteMessage(message.messageId) }
            4 -> {
                replyToMessageId = message.messageId
                setReplyBanner(message)
                val input = findViewById<EditText>(R.id.messageInput)
                input.requestFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
            }
            5 -> {
                lifecycleScope.launch(Dispatchers.IO + crashHandler) {
                    val username = chatRepository.getChatById(chatId)?.username?.trim()
                    val link = if (!username.isNullOrEmpty()) "https://t.me/$username/${message.messageId}" else "https://t.me/c/${if (chatId < 0) chatId.toString().removePrefix("-100") else chatId}/${message.messageId}"
                    withContext(Dispatchers.Main) {
                        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", link))
                        Toast.makeText(this@ChatActivity, "链接已复制", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            6 -> handleRepeatAction(message, true)
            7 -> showForwardDialog(message)
            8 -> saveMedia(message)
        }
    }

    private fun saveMedia(message: MessageEntity) {
        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (e: Exception) { JSONObject() }
            var fileId = ""
            var subDir = ""
            var fName = "${chatId}_${message.messageId}_${System.currentTimeMillis()}"
            
            if (rawObj.has("photo")) {
                val arr = rawObj.getJSONArray("photo")
                fileId = arr.getJSONObject(arr.length() - 1).getString("file_id")
                subDir = "Images"
                fName += ".jpg"
            } else if (rawObj.has("sticker")) {
                fileId = rawObj.getJSONObject("sticker").getString("file_id")
                subDir = "Stickers"
                fName += ".webp"
            } else if (rawObj.has("video")) {
                val vid = rawObj.getJSONObject("video")
                fileId = vid.getString("file_id")
                subDir = "Videos"
                fName = vid.optString("file_name", "$fName.mp4")
            } else if (rawObj.has("document")) {
                val doc = rawObj.getJSONObject("document")
                fileId = doc.getString("file_id")
                subDir = "Files"
                val origName = doc.optString("file_name", "")
                if (origName.isNotEmpty() && origName.contains(".")) {
                    fName += ".${origName.substringAfterLast('.')}"
                }
            }

            val url = FileHelper.getTelegramFileUrl(fileId, token)
            if (!url.isNullOrEmpty()) {
                val success = FileHelper.saveMediaToStorage(this@ChatActivity, url, subDir, fName)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, if (success) "已保存到设定目录" else "保存失败，请检查路径权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleRepeatAction(message: MessageEntity, isForward: Boolean) {
        val prefs = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("repeat_confirm", true)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(if (isForward) "转发式复读确认" else "复读确认")
                .setMessage(if (isForward) "确定要将这条消息以转发形式重新发送到当前会话吗？" else "确定要将这条消息重新发送吗？")
                .setPositiveButton("确定") { _, _ -> if (isForward) forwardMessage(message, chatId) else sendTextMessage(message.text ?: "", null) }
                .setNegativeButton("取消", null).show()
        } else {
            if (isForward) forwardMessage(message, chatId) else sendTextMessage(message.text ?: "", null)
        }
    }

    private fun showForwardDialog(message: MessageEntity) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val chats = chatRepository.getAllChatsList()
            withContext(Dispatchers.Main) {
                if (chats.isEmpty()) return@withContext
                MaterialAlertDialogBuilder(this@ChatActivity)
                    .setTitle("转发给...")
                    .setItems(chats.map { if (it.type == "private") it.firstName ?: "私聊" else it.title ?: "群组" }.toTypedArray()) { _, which ->
                        forwardMessage(message, chats[which].chatId)
                    }.show()
            }
        }
    }

    private fun forwardMessage(message: MessageEntity, targetChatId: Long) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val jsonBody = JSONObject().apply { put("chat_id", targetChatId); put("from_chat_id", message.chatId); put("message_id", message.messageId) }
            val req = Request.Builder().url("https://api.telegram.org/bot$token/forwardMessage").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
            val res = ApiClient.getClient().newCall(req).execute()
            if (res.isSuccessful) {
                val msg = JSONObject(res.body?.string() ?: "")
                if (msg.getBoolean("ok")) {
                    val result = msg.getJSONObject("result")
                    val messageEntity = MessageEntity(
                        messageId = result.getLong("message_id"),
                        chatId = targetChatId,
                        senderUserId = null,
                        senderName = "我",
                        text = result.optString("text", "[媒体/转发消息]"),
                        date = result.getLong("date"),
                        isOutgoing = true,
                        rawJson = result.toString(),
                        entities = null,
                        replyToJson = null,
                        senderRole = null,
                        senderTitle = null,
                        isDeleted = false
                    )
                    messageRepository.insertMessage(messageEntity)
                    chatRepository.updateLastMessage(targetChatId, messageEntity.text ?: "", messageEntity.date * 1000)
                    
                    if (targetChatId == chatId) loadMessagesInternal()
                    else withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "已转发", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private suspend fun deleteMessage(messageId: Long) {
        try {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return
            val res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/deleteMessage?chat_id=$chatId&message_id=$messageId").build()).execute()
            if (res.isSuccessful) {
                messageRepository.markMessageAsDeleted(messageId, chatId)
                withContext(Dispatchers.Main) { 
                    Toast.makeText(this@ChatActivity, "已撤回", Toast.LENGTH_SHORT).show()
                    loadMessagesInternal()
                }
            } else {
                withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "撤回失败", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "撤回异常: ${e.message}", Toast.LENGTH_SHORT).show() } }
    }

    private fun sendTextMessage(text: String, replyTo: Long?) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val jsonBody = JSONObject().apply { put("chat_id", chatId); put("text", text); replyTo?.let { put("reply_to_message_id", it) } }
            val req = Request.Builder().url("https://api.telegram.org/bot$token/sendMessage").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
            val res = ApiClient.getClient().newCall(req).execute()
            if (res.isSuccessful) {
                val msg = JSONObject(res.body?.string() ?: "")
                if (msg.getBoolean("ok")) {
                    val result = msg.getJSONObject("result")
                    var replyToJson: String? = null
                    if (replyTo != null) {
                        try {
                            val repliedMsg = messageRepository.getMessages(chatId).find { it.messageId == replyTo }
                            replyToJson = repliedMsg?.rawJson
                        } catch (_: Exception) {}
                    }
                    val messageEntity = MessageEntity(
                        messageId = result.getLong("message_id"),
                        chatId = chatId,
                        senderUserId = null,
                        senderName = "我",
                        text = text,
                        date = result.getLong("date"),
                        isOutgoing = true,
                        rawJson = result.toString(),
                        entities = null,
                        replyToJson = replyToJson,
                        senderRole = null,
                        senderTitle = null,
                        isDeleted = false
                    )
                    messageRepository.insertMessage(messageEntity)
                    chatRepository.updateLastMessage(chatId, text, result.getLong("date") * 1000)
                    loadMessagesInternal()
                }
            }
        }
    }

    private fun gotoCrash(throwable: Throwable) {
        startActivity(Intent(this, CrashActivity::class.java).apply { putExtra("stack_trace", android.util.Log.getStackTraceString(throwable)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
