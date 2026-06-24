package com.incalr26.botgram.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatActivity : AppCompatActivity() {
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private var chatId: Long = 0
    private var replyToMessageId: Long? = null
    private var chatType: String = "private"

    // 支持多选媒体画廊
    private val pendingUploads = mutableListOf<Pair<Uri, String>>()

    private val crashHandler = CoroutineExceptionHandler { _, throwable -> gotoCrash(throwable) }

    // 使用支持多选的原生选择器
    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handleMediaSelected(uris, "photo") }
    private val pickVideosLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handleMediaSelected(uris, "video") }
    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handleMediaSelected(uris, "document") }
    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handleMediaSelected(uris, "audio") }

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

            fun updateSendButtonState() {
                val hasText = messageInput.text.toString().trim().isNotEmpty()
                val hasMedia = pendingUploads.isNotEmpty()
                if (hasText || hasMedia) {
                    sendButton.isEnabled = true
                    sendButton.clearColorFilter()
                } else {
                    sendButton.isEnabled = false
                    sendButton.setColorFilter(Color.parseColor("#9E9E9E"), PorterDuff.Mode.SRC_IN)
                }
            }
            updateSendButtonState()

            messageInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSendButtonState() }
                override fun afterTextChanged(s: Editable?) {}
            })

            attachButton.setOnClickListener { showAttachMenu(attachButton) }

            findViewById<ImageButton>(R.id.cancelReply).setOnClickListener {
                replyToMessageId = null
                replyBanner.visibility = View.GONE
            }

            sendButton.setOnClickListener {
                val text = messageInput.text.toString().trim()
                if (pendingUploads.isNotEmpty()) {
                    uploadMediaAndSend(text)
                } else if (text.isNotEmpty()) {
                    sendTextMessage(text, replyToMessageId)
                }
                
                replyToMessageId = null
                replyBanner.visibility = View.GONE
                messageInput.text.clear()
                pendingUploads.clear()
                updatePreviewUI()
                updateSendButtonState()
            }

            lifecycleScope.launch(Dispatchers.IO + crashHandler) {
                loadTitleAndType() // 这里包含了正确的 memberCount 获取 API
                loadMessagesInternal()
                chatRepository.updateUnreadCount(chatId, 0)
            }

            NewMessageNotifier.newMessage.observe(this) {
                lifecycleScope.launch(Dispatchers.IO) { delay(100); loadMessagesInternal() }
            }

        } catch (e: Exception) { gotoCrash(e) }
    }

    private fun handleMediaSelected(uris: List<Uri>?, type: String) {
        if (uris.isNullOrEmpty()) return
        
        // 限制互斥：已有文件时不允许混选不同种类的系统媒体
        if (pendingUploads.isNotEmpty() && pendingUploads.first().second != type) {
            Toast.makeText(this, "暂不支持混搭不同类型的媒体发送", Toast.LENGTH_SHORT).show()
            pendingUploads.clear()
        }
        
        uris.forEach { pendingUploads.add(Pair(it, type)) }
        updatePreviewUI()
        
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        sendButton.isEnabled = true
        sendButton.clearColorFilter()
    }

    // 动态生成横向画廊预览
    private fun updatePreviewUI() {
        val previewContainer = findViewById<LinearLayout>(R.id.mediaPreviewContainer)
        val previewList = findViewById<LinearLayout>(R.id.previewList)
        
        if (pendingUploads.isEmpty()) {
            previewContainer.visibility = View.GONE
            return
        }
        
        previewContainer.visibility = View.VISIBLE
        previewList.removeAllViews()

        val density = resources.displayMetrics.density
        
        for ((index, item) in pendingUploads.withIndex()) {
            val uri = item.first
            val type = item.second

            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams((64 * density).toInt(), (64 * density).toInt()).apply {
                    marginEnd = (8 * density).toInt()
                }
            }

            val card = MaterialCardView(this).apply {
                layoutParams = FrameLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt()).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                }
                radius = 8 * density
                setCardBackgroundColor(Color.parseColor("#E0E0E0"))
                cardElevation = 0f
            }

            val icon = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                when (type) {
                    "photo" -> setImageURI(uri)
                    "video" -> { setImageResource(android.R.drawable.presence_video_online); setPadding(24, 24, 24, 24) }
                    "audio" -> { setImageResource(android.R.drawable.ic_media_play); setPadding(24, 24, 24, 24) }
                    else -> { setImageResource(R.drawable.ic_file_document); setPadding(24, 24, 24, 24); setColorFilter(Color.GRAY) }
                }
            }
            card.addView(icon)

            val closeBtn = ImageButton(this).apply {
                layoutParams = FrameLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()).apply {
                    gravity = Gravity.TOP or Gravity.END
                }
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = ContextCompat.getDrawable(this@ChatActivity, R.drawable.avatar_bg) // 使用圆形黑底
                setColorFilter(Color.WHITE)
                setPadding(8, 8, 8, 8)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setOnClickListener {
                    pendingUploads.removeAt(index)
                    updatePreviewUI()
                    if (pendingUploads.isEmpty()) {
                        val input = findViewById<EditText>(R.id.messageInput)
                        if (input.text.toString().trim().isEmpty()) {
                            val btn = findViewById<ImageButton>(R.id.sendButton)
                            btn.isEnabled = false
                            btn.setColorFilter(Color.parseColor("#9E9E9E"), PorterDuff.Mode.SRC_IN)
                        }
                    }
                }
            }

            frame.addView(card)
            frame.addView(closeBtn)
            previewList.addView(frame)
        }
    }

    private fun showAttachMenu(anchor: View) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@ChatActivity, R.drawable.popup_menu_background)
            elevation = 16f * resources.displayMetrics.density
            clipToOutline = true
        }

        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val textColor = typedValue.data
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data

        val items = listOf(
            Triple("发送图片", android.R.drawable.ic_menu_gallery, 1),
            Triple("发送视频", android.R.drawable.presence_video_online, 2),
            Triple("发送音频", android.R.drawable.ic_media_play, 4),
            Triple("发送文件", R.drawable.ic_file_document, 3)
        )

        val popupWindow = PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ContextCompat.getDrawable(this@ChatActivity, android.R.color.transparent))
        }

        items.forEach { (title, iconRes, action) ->
            val itemView = layoutInflater.inflate(R.layout.item_popup_menu, container, false)
            val layoutParams = LinearLayout.LayoutParams((24 * resources.displayMetrics.density).toInt(), (24 * resources.displayMetrics.density).toInt())
            itemView.findViewById<ImageView>(R.id.menu_icon).apply { 
                this.layoutParams = layoutParams
                setImageResource(iconRes)
                setColorFilter(primaryColor) 
            }
            itemView.findViewById<TextView>(R.id.menu_text).apply { 
                text = title
                setTextColor(textColor) 
            }
            itemView.setOnClickListener { 
                popupWindow.dismiss()
                when (action) {
                    1 -> pickImagesLauncher.launch("image/*")
                    2 -> pickVideosLauncher.launch("video/*")
                    3 -> pickFilesLauncher.launch("*/*")
                    4 -> pickAudioLauncher.launch("audio/*")
                }
            }
            container.addView(itemView)
        }

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = container.measuredHeight
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        
        val xPos = anchorLocation[0]
        val yPos = anchorLocation[1] - popupHeight - (16 * resources.displayMetrics.density).toInt()
        popupWindow.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, xPos, yPos)
    }

    private fun uploadMediaAndSend(caption: String) {
        if (pendingUploads.isEmpty()) return
        Toast.makeText(this, "正在上传发送...", Toast.LENGTH_SHORT).show()
        val uploadsSnapshot = pendingUploads.toList()
        
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            
            // 如果是单个文件，直接使用老接口
            if (uploadsSnapshot.size == 1) {
                val item = uploadsSnapshot.first()
                val type = item.second
                val file = FileHelper.uriToTempFile(this@ChatActivity, item.first) ?: return@launch

                val mimeType = contentResolver.getType(item.first) ?: "application/octet-stream"
                val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart(type, file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))

                if (caption.isNotEmpty()) requestBodyBuilder.addFormDataPart("caption", caption)
                replyToMessageId?.let { requestBodyBuilder.addFormDataPart("reply_to_message_id", it.toString()) }

                val apiMethod = when(type) {
                    "photo" -> "sendPhoto"
                    "video" -> "sendVideo"
                    "audio" -> "sendAudio"
                    else -> "sendDocument"
                }

                val req = Request.Builder().url("https://api.telegram.org/bot$token/$apiMethod").post(requestBodyBuilder.build()).build()
                val res = ApiClient.getClient().newCall(req).execute()
                file.delete()

                if (res.isSuccessful) {
                    val msg = JSONObject(res.body?.string() ?: "")
                    if (msg.getBoolean("ok")) {
                        insertAndRefresh(msg.getJSONObject("result"), caption, type)
                    }
                }
            } else {
                // 如果是多个文件，触发高级 sendMediaGroup (相册) 逻辑
                val mediaArray = JSONArray()
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                builder.addFormDataPart("chat_id", chatId.toString())
                replyToMessageId?.let { builder.addFormDataPart("reply_to_message_id", it.toString()) }

                val tempFiles = mutableListOf<File>()
                
                uploadsSnapshot.forEachIndexed { index, pair ->
                    val uri = pair.first
                    val type = if (pair.second == "audio" || pair.second == "document") "document" else pair.second
                    val file = FileHelper.uriToTempFile(this@ChatActivity, uri)
                    
                    if (file != null) {
                        tempFiles.add(file)
                        val mediaObj = JSONObject().apply {
                            put("type", type)
                            put("media", "attach://file$index")
                            if (index == 0 && caption.isNotEmpty()) put("caption", caption)
                        }
                        mediaArray.put(mediaObj)
                        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                        builder.addFormDataPart("file$index", file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
                    }
                }
                
                builder.addFormDataPart("media", mediaArray.toString())
                val req = Request.Builder().url("https://api.telegram.org/bot$token/sendMediaGroup").post(builder.build()).build()
                val res = ApiClient.getClient().newCall(req).execute()
                
                tempFiles.forEach { it.delete() } // 统一清理残渣

                if (res.isSuccessful) {
                    val msg = JSONObject(res.body?.string() ?: "")
                    if (msg.getBoolean("ok")) {
                        val resultArray = msg.optJSONArray("result")
                        if (resultArray != null) {
                            for (i in 0 until resultArray.length()) {
                                insertAndRefresh(resultArray.getJSONObject(i), caption, "group")
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun insertAndRefresh(result: JSONObject, caption: String, typeLabel: String) {
        val messageEntity = MessageEntity(
            messageId = result.getLong("message_id"),
            chatId = chatId,
            senderUserId = null,
            senderName = "我",
            text = caption,
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
        chatRepository.updateLastMessage(chatId, if (caption.isNotEmpty()) caption else "[媒体]", result.getLong("date") * 1000)
        loadMessagesInternal()
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
            // 独立查询成员数量以保证可靠获取
            val countReq = Request.Builder().url("https://api.telegram.org/bot$token/getChatMemberCount?chat_id=$chatId").build()
            val countRes = ApiClient.getClient().newCall(countReq).execute()
            if (countRes.isSuccessful) {
                val json = JSONObject(countRes.body?.string() ?: "")
                if (json.getBoolean("ok")) memberCount = json.getInt("result")
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
        
        // 绝对坐标防越界阻截：如果菜单会弹出屏幕，就翻转到气泡上方！
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val screenHeight = resources.displayMetrics.heightPixels

        var yPos = anchorLocation[1] + anchor.height
        if (yPos + popupHeight > screenHeight) {
            yPos = anchorLocation[1] - popupHeight
        }
        if (yPos < 0) yPos = 50

        popupWindow.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, anchorLocation[0], yPos)
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
            } else if (rawObj.has("document") || rawObj.has("audio")) {
                val isAudio = rawObj.has("audio")
                val doc = if (isAudio) rawObj.getJSONObject("audio") else rawObj.getJSONObject("document")
                fileId = doc.getString("file_id")
                subDir = if (isAudio) "Audio" else "Files"
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
