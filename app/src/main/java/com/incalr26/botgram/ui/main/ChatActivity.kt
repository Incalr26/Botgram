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
import android.text.Html
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChatActivity : AppCompatActivity() {
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private var chatId: Long = 0
    private var replyToMessageId: Long? = null
    private var chatType: String = "private"
    
    private var editingMessageId: Long? = null
    private val pendingUploads = mutableListOf<Pair<Uri, String>>()
    private val crashHandler = CoroutineExceptionHandler { _, throwable -> gotoCrash(throwable) }

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
            toolbar.setSubtitleTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodySmall)

            toolbar.setOnClickListener { startActivity(Intent(this, ChatInfoActivity::class.java).apply { putExtra("chatId", chatId) }) }

            chatId = intent.getLongExtra("chatId", 0)
            if (chatId == 0L) { finish(); return }

            val app = BotApp.instance
            chatRepository = ChatRepository(app.databaseHelper)
            messageRepository = MessageRepository(app.databaseHelper)

            val messageInput = findViewById<EditText>(R.id.messageInput)

            adapter = MessageAdapter(
                onClick = { message, view ->
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (imm.isActive && currentFocus != null) imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
                    view.postDelayed({ if (!isFinishing && !isDestroyed) showMessageMenu(message, view) }, 150)
                },
                onLongClick = { message, view ->
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (imm.isActive && currentFocus != null) imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
                    view.postDelayed({ if (!isFinishing && !isDestroyed) showMessageMenu(message, view) }, 150)
                    true
                },
                onAvatarClick = { message ->
                    val targetId = message.senderUserId ?: chatId
                    startActivity(Intent(this, ChatInfoActivity::class.java).apply { putExtra("chatId", targetId) })
                },
                onAvatarLongClick = { message ->
                    val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (_: Exception) { JSONObject() }
                    val userObj = rawObj.optJSONObject("from")
                    val mentionText = if (userObj != null && userObj.has("username")) "@${userObj.getString("username")} " else "${message.senderName ?: "用户"} "
                    val currentText = messageInput.text.toString()
                    messageInput.setText(currentText + mentionText)
                    messageInput.setSelection(messageInput.text.length)
                    messageInput.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(messageInput, 0)
                },
                onFileClick = { message -> handleFileClick(message) },
                onReactionToggle = { message, emoji -> toggleReaction(message, emoji) }
            )

            recyclerView = findViewById(R.id.messagesRecyclerView)
            recyclerView.adapter = adapter
            val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
            recyclerView.layoutManager = layoutManager
            
            val fabScrollDown = findViewById<View>(R.id.fabScrollDown)
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (!recyclerView.canScrollVertically(1)) fabScrollDown.visibility = View.GONE
                }
            })
            fabScrollDown.setOnClickListener {
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                it.visibility = View.GONE
            }

            val sendButton = findViewById<ImageButton>(R.id.sendButton)
            val attachButton = findViewById<ImageButton>(R.id.attachButton)
            val replyBanner = findViewById<LinearLayout>(R.id.replyBanner)

            fun updateSendButtonState() {
                val hasText = messageInput.text.toString().trim().isNotEmpty()
                val hasMedia = pendingUploads.isNotEmpty()
                val tv = TypedValue(); theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
                if (hasText || hasMedia) { sendButton.isEnabled = true; sendButton.setColorFilter(tv.data, PorterDuff.Mode.SRC_IN); sendButton.alpha = 1.0f }
                else { sendButton.isEnabled = false; sendButton.setColorFilter(Color.parseColor("#9E9E9E"), PorterDuff.Mode.SRC_IN); sendButton.alpha = 0.5f }
            }
            updateSendButtonState()

            messageInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSendButtonState() }
                override fun afterTextChanged(s: Editable?) {}
            })

            attachButton.setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (imm.isActive(messageInput)) { imm.hideSoftInputFromWindow(messageInput.windowToken, 0); attachButton.postDelayed({ showAttachMenu(attachButton) }, 250) }
                else showAttachMenu(attachButton)
            }

            findViewById<ImageButton>(R.id.cancelReply).setOnClickListener {
                replyToMessageId = null; editingMessageId = null
                sendButton.setImageResource(R.drawable.ic_send)
                replyBanner.visibility = View.GONE
                messageInput.text.clear()
            }

            sendButton.setOnClickListener {
                val text = messageInput.text.toString().trim()
                if (editingMessageId != null) editSelfMessage(text, editingMessageId!!)
                else { if (pendingUploads.isNotEmpty()) uploadMediaAndSend(text) else if (text.isNotEmpty()) sendTextMessage(text, replyToMessageId, null) }
                replyToMessageId = null; editingMessageId = null
                sendButton.setImageResource(R.drawable.ic_send)
                replyBanner.visibility = View.GONE
                messageInput.text.clear(); pendingUploads.clear(); updatePreviewUI(); updateSendButtonState()
            }

            lifecycleScope.launch(Dispatchers.IO + crashHandler) { loadTitleAndType(); loadMessagesInternal(true); chatRepository.updateUnreadCount(chatId, 0) }
            NewMessageNotifier.newMessage.observe(this) { lifecycleScope.launch(Dispatchers.IO) { delay(100); loadMessagesInternal(false) } }
        } catch (e: Exception) { gotoCrash(e) }
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }

    private fun toggleReaction(message: MessageEntity, emoji: String) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val arr = try { JSONArray(message.reactions ?: "[]") } catch(e:Exception){ JSONArray() }
            var alreadyChosen = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("emoji") == emoji && o.optBoolean("chosen", false)) { alreadyChosen = true; break }
            }

            val reactionJson = JSONObject().apply {
                put("chat_id", chatId); put("message_id", message.messageId)
                if (!alreadyChosen) put("reaction", JSONArray().apply { put(JSONObject().apply { put("type", "emoji"); put("emoji", emoji) }) })
                else put("reaction", JSONArray())
            }
            
            val req = Request.Builder().url("https://api.telegram.org/bot$token/setMessageReaction").post(reactionJson.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
            val res = ApiClient.getClient().newCall(req).execute()
            if (res.isSuccessful) {
                val nextArr = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("emoji") == emoji) {
                        if (alreadyChosen) { val count = o.optInt("count", 1) - 1; if (count > 0) { o.put("count", count); o.put("chosen", false); nextArr.put(o) } } 
                        else { o.put("count", o.optInt("count", 1) + 1); o.put("chosen", true); nextArr.put(o) }
                    } else { nextArr.put(o) }
                }
                if (!alreadyChosen && arr.toString().indexOf("\"emoji\":\"$emoji\"") == -1) {
                    nextArr.put(JSONObject().apply { put("emoji", emoji); put("count", 1); put("chosen", true) })
                }
                messageRepository.insertMessage(message.copy(reactions = nextArr.toString()))
                withContext(Dispatchers.Main) { loadMessagesInternal(false) }
            }
        }
    }

    private fun handleFileClick(message: MessageEntity) {
        val fileCachePrefs = getSharedPreferences("botgram_file_cache", Context.MODE_PRIVATE)
        val savedPath = fileCachePrefs.getString(message.messageId.toString(), null)
        if (savedPath != null && File(savedPath).exists()) { openSystemFile(File(savedPath)) } else {
            Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.IO + crashHandler) {
                val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
                val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (e: Exception) { JSONObject() }
                var fileId = ""; var subDir = "Files"; var origName = "file_${message.messageId}"
                if (rawObj.has("document")) { val doc = rawObj.getJSONObject("document"); fileId = doc.getString("file_id"); origName = doc.optString("file_name", origName) }
                else if (rawObj.has("audio") || rawObj.has("voice")) { val isAudio = rawObj.has("audio"); val doc = if (isAudio) rawObj.getJSONObject("audio") else rawObj.getJSONObject("voice"); fileId = doc.getString("file_id"); subDir = "Audio"; origName = doc.optString("file_name", if (isAudio) "audio.mp3" else "voice.ogg") } else return@launch

                val url = FileHelper.getTelegramFileUrl(fileId, token)
                if (!url.isNullOrEmpty()) {
                    val savedFile = FileHelper.saveMediaToStorageAndGetFile(this@ChatActivity, url, subDir, origName)
                    withContext(Dispatchers.Main) {
                        if (savedFile != null) { fileCachePrefs.edit().putString(message.messageId.toString(), savedFile.absolutePath).apply(); Toast.makeText(this@ChatActivity, "下载完成，即将打开", Toast.LENGTH_SHORT).show(); openSystemFile(savedFile) }
                        else Toast.makeText(this@ChatActivity, "下载失败，请检查网络或权限", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun openSystemFile(file: File) {
        try {
            val uri = Uri.parse("file://${file.absolutePath}"); val intent = Intent(Intent.ACTION_VIEW)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder().build())
            intent.setDataAndType(uri, mime); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) { Toast.makeText(this, "无法打开该类型文件", Toast.LENGTH_SHORT).show() }
    }

    private fun handleMediaSelected(uris: List<Uri>?, type: String) {
        if (uris.isNullOrEmpty()) return
        if (pendingUploads.isNotEmpty() && pendingUploads.first().second != type) { Toast.makeText(this, "暂不支持混搭不同类型的媒体发送", Toast.LENGTH_SHORT).show(); pendingUploads.clear() }
        uris.forEach { pendingUploads.add(Pair(it, type)) }; updatePreviewUI()
        findViewById<ImageButton>(R.id.sendButton).apply { isEnabled = true; clearColorFilter(); alpha = 1.0f; val tv = TypedValue(); theme.resolveAttribute(android.R.attr.colorPrimary, tv, true); setColorFilter(tv.data, PorterDuff.Mode.SRC_IN) }
    }

    private fun updatePreviewUI() {
        val previewContainer = findViewById<LinearLayout>(R.id.mediaPreviewContainer)
        val previewList = findViewById<LinearLayout>(R.id.previewList)
        if (pendingUploads.isEmpty()) { previewContainer.visibility = View.GONE; return }
        previewContainer.visibility = View.VISIBLE; previewList.removeAllViews()
        val density = resources.displayMetrics.density; val tv = TypedValue(); theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
        
        for ((index, item) in pendingUploads.withIndex()) {
            val frame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams((64 * density).toInt(), (64 * density).toInt()).apply { marginEnd = (8 * density).toInt() } }
            val card = MaterialCardView(this).apply { layoutParams = FrameLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt()).apply { gravity = Gravity.BOTTOM or Gravity.START }; radius = 8 * density; setCardBackgroundColor(Color.parseColor("#E0E0E0")); cardElevation = 0f }
            val icon = ImageView(this).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); when (item.second) { "photo" -> { scaleType = ImageView.ScaleType.CENTER_CROP; setImageURI(item.first) } else -> { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(24, 24, 24, 24); setColorFilter(tv.data); when (item.second) { "video" -> setImageResource(R.drawable.ic_video_solid); "audio" -> setImageResource(R.drawable.ic_audio); else -> setImageResource(R.drawable.ic_file_document) } } } }
            card.addView(icon)
            val closeBtn = ImageButton(this).apply { layoutParams = FrameLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()).apply { gravity = Gravity.TOP or Gravity.END }; setImageResource(R.drawable.ic_close); background = ContextCompat.getDrawable(this@ChatActivity, R.drawable.avatar_bg); setColorFilter(Color.WHITE); setPadding(8, 8, 8, 8); scaleType = ImageView.ScaleType.FIT_CENTER; setOnClickListener { pendingUploads.removeAt(index); updatePreviewUI(); if (pendingUploads.isEmpty() && findViewById<EditText>(R.id.messageInput).text.toString().trim().isEmpty()) { val btn = findViewById<ImageButton>(R.id.sendButton); btn.isEnabled = false; btn.setColorFilter(Color.parseColor("#9E9E9E"), PorterDuff.Mode.SRC_IN); btn.alpha = 0.5f } } }
            frame.addView(card); frame.addView(closeBtn); previewList.addView(frame)
        }
    }

    private fun showAttachMenu(anchor: View) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = ContextCompat.getDrawable(this@ChatActivity, R.drawable.popup_menu_background); elevation = 12f * resources.displayMetrics.density; clipToOutline = true }
        val tvText = TypedValue(); theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tvText, true)
        val tvColor = TypedValue(); theme.resolveAttribute(android.R.attr.colorPrimary, tvColor, true)
        
        val items = listOf(
            Triple("发送图片", R.drawable.ic_photo_solid, 1),
            Triple("发送视频", R.drawable.ic_video_solid, 2),
            Triple("发送音频", R.drawable.ic_audio, 4),
            Triple("发送文件", R.drawable.ic_file_document, 3),
            Triple("发送内联键盘", R.drawable.ic_keyboard, 5)
        )
        val popupWindow = PopupWindow(container, -2, -2, true).apply { isFocusable = true; setBackgroundDrawable(ContextCompat.getDrawable(this@ChatActivity, android.R.color.transparent)) }

        items.forEach { (title, iconRes, action) ->
            val itemView = layoutInflater.inflate(R.layout.item_popup_menu, container, false)
            itemView.findViewById<ImageView>(R.id.menu_icon).apply { layoutParams = LinearLayout.LayoutParams((24 * resources.displayMetrics.density).toInt(), (24 * resources.displayMetrics.density).toInt()); setImageResource(iconRes); setColorFilter(tvColor.data) }
            itemView.findViewById<TextView>(R.id.menu_text).apply { text = title; setTextColor(tvText.data) }
            itemView.setOnClickListener { popupWindow.dismiss(); when (action) { 1 -> pickImagesLauncher.launch("image/*"); 2 -> pickVideosLauncher.launch("video/*"); 3 -> pickFilesLauncher.launch("*/*"); 4 -> pickAudioLauncher.launch("audio/*"); 5 -> showInlineKeyboardDialog() } }
            container.addView(itemView)
        }
        container.measure(0, 0); val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        popupWindow.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, loc[0], loc[1] - container.measuredHeight - (16 * resources.displayMetrics.density).toInt())
    }

    private fun showInlineKeyboardDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 24) }
        val msgInput = EditText(this).apply { hint = "要发送的消息文字..." }
        val btnText = EditText(this).apply { hint = "按钮显示的文字" }
        val btnUrl = EditText(this).apply { hint = "按钮跳转的 URL (可选)" }
        layout.addView(msgInput); layout.addView(btnText); layout.addView(btnUrl)
        
        MaterialAlertDialogBuilder(this).setTitle("发送内联键盘").setView(layout).setPositiveButton("发送") { _, _ ->
            val txt = msgInput.text.toString(); val bt = btnText.text.toString(); val bu = btnUrl.text.toString()
            if (txt.isNotEmpty() && bt.isNotEmpty()) {
                val keyboard = JSONObject().apply { put("inline_keyboard", JSONArray().apply { put(JSONArray().apply { put(JSONObject().apply { put("text", bt); put("url", bu.takeIf { it.isNotEmpty() } ?: "https://t.me") }) }) }) }
                sendTextMessage(txt, null, keyboard.toString())
            }
        }.setNegativeButton("取消", null).show()
    }

    private fun uploadMediaAndSend(caption: String) {
        if (pendingUploads.isEmpty()) return
        Toast.makeText(this, "正在后台上传...", Toast.LENGTH_SHORT).show()
        val uploadsSnapshot = pendingUploads.toList()
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val uploadClient = ApiClient.getClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.HOURS).readTimeout(12, TimeUnit.HOURS).build()

            if (uploadsSnapshot.size == 1) {
                val item = uploadsSnapshot.first(); val type = item.second; val file = FileHelper.uriToTempFile(this@ChatActivity, item.first) ?: return@launch
                val mimeType = contentResolver.getType(item.first) ?: "application/octet-stream"
                val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", chatId.toString()).addFormDataPart(if (type == "audio") "document" else type, file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
                if (caption.isNotEmpty()) requestBodyBuilder.addFormDataPart("caption", caption)
                replyToMessageId?.let { requestBodyBuilder.addFormDataPart("reply_to_message_id", it.toString()) }
                val apiMethod = when(type) { "photo" -> "sendPhoto"; "video" -> "sendVideo"; else -> "sendDocument" }
                val req = Request.Builder().url("https://api.telegram.org/bot$token/$apiMethod").post(requestBodyBuilder.build()).build()
                val res = uploadClient.newCall(req).execute(); file.delete()
                if (res.isSuccessful) { val msg = JSONObject(res.body?.string() ?: ""); if (msg.getBoolean("ok")) insertAndRefresh(msg.getJSONObject("result"), caption) }
            } else {
                val mediaArray = JSONArray(); val builder = MultipartBody.Builder().setType(MultipartBody.FORM); builder.addFormDataPart("chat_id", chatId.toString())
                replyToMessageId?.let { builder.addFormDataPart("reply_to_message_id", it.toString()) }; val tempFiles = mutableListOf<File>()
                uploadsSnapshot.forEachIndexed { index, pair ->
                    val type = if (pair.second == "audio" || pair.second == "document") "document" else pair.second
                    val file = FileHelper.uriToTempFile(this@ChatActivity, pair.first)
                    if (file != null) {
                        tempFiles.add(file); val mediaObj = JSONObject().apply { put("type", type); put("media", "attach://file$index"); if (index == 0 && caption.isNotEmpty()) put("caption", caption) }
                        mediaArray.put(mediaObj); val mimeType = contentResolver.getType(pair.first) ?: "application/octet-stream"; builder.addFormDataPart("file$index", file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
                    }
                }
                builder.addFormDataPart("media", mediaArray.toString()); val req = Request.Builder().url("https://api.telegram.org/bot$token/sendMediaGroup").post(builder.build()).build()
                val res = uploadClient.newCall(req).execute(); tempFiles.forEach { it.delete() }
                if (res.isSuccessful) {
                    val msg = JSONObject(res.body?.string() ?: ""); if (msg.getBoolean("ok")) { val resultArray = msg.optJSONArray("result"); if (resultArray != null) { for (i in 0 until resultArray.length()) insertAndRefresh(resultArray.getJSONObject(i), caption) } }
                }
            }
        }
    }

    private suspend fun insertAndRefresh(result: JSONObject, caption: String) {
        val messageEntity = MessageEntity(messageId = result.getLong("message_id"), chatId = chatId, senderUserId = null, senderName = "我", text = caption, date = result.getLong("date"), isOutgoing = true, rawJson = result.toString(), entities = null, replyToJson = null, senderRole = null, senderTitle = null, isDeleted = false, isEdited = false, editHistory = null, reactions = null)
        messageRepository.insertMessage(messageEntity); chatRepository.updateLastMessage(chatId, if (caption.isNotEmpty()) caption else "[媒体]", result.getLong("date") * 1000); loadMessagesInternal(true)
    }

    private fun setReplyBanner(message: MessageEntity) {
        findViewById<TextView>(R.id.replyText).text = "回复 ${message.senderName ?: "未知"}: ${message.text?.take(50) ?: ""}"; findViewById<LinearLayout>(R.id.replyBanner).visibility = View.VISIBLE
    }

    private fun getStatusBarHeight(): Int { val res = resources.getIdentifier("status_bar_height", "dimen", "android"); return if (res > 0) resources.getDimensionPixelSize(res) else 0 }

    private suspend fun loadTitleAndType() {
        val chat = chatRepository.getChatById(chatId)
        try {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: ""
            ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/getChatMemberCount?chat_id=$chatId").build()).execute()
        } catch (_: Exception) {}

        withContext(Dispatchers.Main) {
            if (chat != null) {
                chatType = chat.type; val username = chat.username
                supportActionBar?.title = if (chat.type == "private") chat.firstName ?: username ?: "私聊" else chat.title ?: "群组"
                val typeStr = when (chat.type) { "private" -> "私聊"; "group" -> "私密群组"; "supergroup" -> if (!username.isNullOrEmpty()) "公开群组" else "私密群组"; "channel" -> if (!username.isNullOrEmpty()) "公开频道" else "私密频道"; else -> chat.type }
                supportActionBar?.subtitle = typeStr
            }
        }
    }

    private suspend fun loadMessagesInternal(forceScroll: Boolean = false) {
        val messages = messageRepository.getMessages(chatId)
        withContext(Dispatchers.Main) {
            val list = messages.toList()
            val isAtBottom = !recyclerView.canScrollVertically(1)
            adapter.submitList(list) {
                if (list.isNotEmpty()) {
                    if (isAtBottom || forceScroll) recyclerView.scrollToPosition(list.size - 1)
                    else findViewById<View>(R.id.fabScrollDown).visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showMessageMenu(message: MessageEntity, anchor: View) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = ContextCompat.getDrawable(this@ChatActivity, R.drawable.popup_menu_background); elevation = 12f * resources.displayMetrics.density; clipToOutline = true }
        val tvText = TypedValue(); theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tvText, true)

        val items = mutableListOf(
            Triple("复制", R.drawable.ic_copy, 1),
            Triple("复读", R.drawable.ic_repeat, 2),
            Triple("转发式复读", R.drawable.ic_repeat, 6),
            Triple("转发给...", R.drawable.ic_send, 7),
            Triple("回复", R.drawable.ic_reply, 4),
            Triple("添加/取消回应", R.drawable.ic_smile, 11)
        )
        if (chatType != "private") items.add(2, Triple("复制链接", R.drawable.ic_link, 5))
        if (message.isOutgoing) { items.add(Triple("编辑", R.drawable.ic_edit, 9)); items.add(Triple("撤回", R.drawable.ic_revoke, 3)) }
        if (!message.editHistory.isNullOrEmpty() && message.editHistory != "[]") items.add(Triple("编辑历史", R.drawable.ic_history, 10))

        val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (e: Exception) { JSONObject() }
        if (rawObj.has("photo") || rawObj.has("sticker") || rawObj.has("video") || rawObj.has("document") || rawObj.has("audio") || rawObj.has("voice")) { items.add(0, Triple("保存", R.drawable.ic_save_media, 8)) }

        val popupWindow = PopupWindow(container, -2, -2, true).apply { isFocusable = true; setBackgroundDrawable(ContextCompat.getDrawable(this@ChatActivity, android.R.color.transparent)) }

        items.forEach { (title, iconRes, action) ->
            val itemView = layoutInflater.inflate(R.layout.item_popup_menu, container, false)
            itemView.findViewById<ImageView>(R.id.menu_icon).apply { setImageResource(iconRes) }
            itemView.findViewById<TextView>(R.id.menu_text).apply { text = title; setTextColor(tvText.data) }
            itemView.setOnClickListener { handleMenuAction(action, message); popupWindow.dismiss() }
            container.addView(itemView)
        }
        container.measure(0, 0); val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        var yPos = loc[1] + anchor.height; if (yPos + container.measuredHeight > resources.displayMetrics.heightPixels) yPos = loc[1] - container.measuredHeight
        popupWindow.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, loc[0], if (yPos < 0) 50 else yPos)
    }

    private fun handleMenuAction(action: Int, message: MessageEntity) {
        when (action) {
            1 -> { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("msg", message.text ?: "")); Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show() }
            2 -> handleRepeatAction(message, false); 3 -> lifecycleScope.launch(Dispatchers.IO + crashHandler) { deleteMessage(message.messageId) }
            4 -> { replyToMessageId = message.messageId; setReplyBanner(message); val input = findViewById<EditText>(R.id.messageInput); input.requestFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0) }
            5 -> lifecycleScope.launch(Dispatchers.IO + crashHandler) {
                val username = chatRepository.getChatById(chatId)?.username?.trim()
                val link = if (!username.isNullOrEmpty()) "https://t.me/$username/${message.messageId}" else "https://t.me/c/${if (chatId < 0) chatId.toString().removePrefix("-100") else chatId}/${message.messageId}"
                withContext(Dispatchers.Main) { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", link)); Toast.makeText(this@ChatActivity, "链接已复制", Toast.LENGTH_SHORT).show() }
            }
            6 -> handleRepeatAction(message, true); 7 -> showForwardDialog(message); 8 -> saveMedia(message)
            9 -> {
                editingMessageId = message.messageId; val input = findViewById<EditText>(R.id.messageInput); input.setText(message.text); input.setSelection(input.text.length)
                val tv = TypedValue(); theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
                findViewById<ImageButton>(R.id.sendButton).apply { setImageResource(R.drawable.ic_check); setColorFilter(tv.data, PorterDuff.Mode.SRC_IN) }
                findViewById<LinearLayout>(R.id.replyBanner).visibility = View.VISIBLE; findViewById<TextView>(R.id.replyText).text = "正在编辑消息"
                input.requestFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
            }
            10 -> showEditHistoryDialog(message.editHistory)
            11 -> showReactionGridDialog(message)
        }
    }

    private fun showReactionGridDialog(message: MessageEntity) {
        val emojis = arrayOf("👍", "👎", "❤", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢", "🎉", "🤩", "🤮", "💩", "🙏", "👌", "🕊", "🤡", "🥱", "🥴", "😍", "🐳", "❤‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨", "😐", "🍓", "🍾", "💋", "🖕", "😈", "😴", "😭", "🤓", "👻", "👨‍💻", "👀", "🎃", "🙈", "😇", "🤝", "✍", "🤗", "🫡", "🎅", "🎄", "☃", "💅", "🤪", "🗿", "🆒", "💘", "🙉", "🦄", "😘", "💊", "😎", "👾", "🤷‍♂", "🤷", "🤷‍♀", "😡")
        val grid = GridLayout(this).apply { columnCount = 6; layoutParams = ViewGroup.LayoutParams(-1, -2); setPadding(32, 48, 32, 48) }
        val tvText = TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tvText, true)
        var dialog: androidx.appcompat.app.AlertDialog? = null
        emojis.forEach { emoji ->
            val tv = TextView(this).apply {
                text = emoji; textSize = 28f; gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply { width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(8, 16, 8, 16) }
                background = ContextCompat.getDrawable(this@ChatActivity, tvText.resourceId)
                setOnClickListener { toggleReaction(message, emoji); dialog?.dismiss() }
            }
            grid.addView(tv)
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        dialog = MaterialAlertDialogBuilder(this).setTitle("添加表情回应").setView(scroll).setNegativeButton("移除全部", { _, _ -> toggleReaction(message, "REMOVE_ALL") }).show()
    }

    private fun showEditHistoryDialog(historyJson: String?) {
        try {
            val arr = JSONArray(historyJson ?: "[]")
            val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 24) }
            val tvText = TypedValue(); theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tvText, true)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val timeStr = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(obj.optLong("date", 0L) * 1000))
                val tv = TextView(this).apply {
                    this.text = Html.fromHtml("<small><font color='#888888'>$timeStr</font></small><br/>${obj.optString("text", "")}", Html.FROM_HTML_MODE_COMPACT)
                    setTextColor(tvText.data); textSize = 15f; setTextIsSelectable(true)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 48 }
                }
                container.addView(tv)
            }
            val scroll = ScrollView(this).apply { addView(container) }
            MaterialAlertDialogBuilder(this).setTitle("编辑历史 (长按选择)").setView(scroll).setPositiveButton("关闭", null).show()
        } catch (e: Exception) { Toast.makeText(this, "解析历史失败", Toast.LENGTH_SHORT).show() }
    }

    private fun editSelfMessage(newText: String, msgId: Long) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val jsonBody = JSONObject().apply { put("chat_id", chatId); put("message_id", msgId); put("text", newText); put("parse_mode", "Markdown") }
            var res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/editMessageText").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()).execute()
            if (!res.isSuccessful) { jsonBody.remove("parse_mode"); res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/editMessageText").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()).execute() }
            if (res.isSuccessful) { val msg = JSONObject(res.body?.string() ?: ""); if (msg.getBoolean("ok")) { val oldMsg = messageRepository.getMessages(chatId).find { it.messageId == msgId }; if (oldMsg != null) messageRepository.insertMessage(oldMsg.copy(text = newText)); withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "已修改", Toast.LENGTH_SHORT).show(); loadMessagesInternal(true) } }
            } else withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "修改失败", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun saveMedia(message: MessageEntity) {
        Toast.makeText(this, "正在尝试保存...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileCachePrefs = getSharedPreferences("botgram_file_cache", Context.MODE_PRIVATE)
                val savedPath = fileCachePrefs.getString(message.messageId.toString(), null)
                if (savedPath != null && File(savedPath).exists()) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "文件已在本地: $savedPath", Toast.LENGTH_LONG).show() }
                } else {
                    withContext(Dispatchers.Main) { handleFileClick(message) }
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { gotoCrash(e) } }
        }
    }

    private fun handleRepeatAction(message: MessageEntity, isForward: Boolean) {
        val prefs = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("repeat_confirm", true)) { MaterialAlertDialogBuilder(this).setTitle(if (isForward) "转发式复读确认" else "复读确认").setMessage(if (isForward) "确定要将这条消息以转发形式重新发送到当前会话吗？" else "确定要将这条消息重新发送吗？").setPositiveButton("确定") { _, _ -> if (isForward) forwardMessage(message, chatId) else sendTextMessage(message.text ?: "", null, null) }.setNegativeButton("取消", null).show()
        } else { if (isForward) forwardMessage(message, chatId) else sendTextMessage(message.text ?: "", null, null) }
    }

    private fun showForwardDialog(message: MessageEntity) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val chats = chatRepository.getAllChatsList()
            withContext(Dispatchers.Main) { if (chats.isEmpty()) return@withContext; MaterialAlertDialogBuilder(this@ChatActivity).setTitle("转发给...").setItems(chats.map { if (it.type == "private") it.firstName ?: "私聊" else it.title ?: "群组" }.toTypedArray()) { _, which -> forwardMessage(message, chats[which].chatId) }.show() }
        }
    }

    private fun forwardMessage(message: MessageEntity, targetChatId: Long) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val jsonBody = JSONObject().apply { put("chat_id", targetChatId); put("from_chat_id", message.chatId); put("message_id", message.messageId) }
            val req = Request.Builder().url("https://api.telegram.org/bot$token/forwardMessage").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build(); val res = ApiClient.getClient().newCall(req).execute()
            if (res.isSuccessful) {
                val msg = JSONObject(res.body?.string() ?: "")
                if (msg.getBoolean("ok")) {
                    val result = msg.getJSONObject("result"); val messageEntity = MessageEntity(messageId = result.getLong("message_id"), chatId = targetChatId, senderUserId = null, senderName = "我", text = result.optString("text", "[媒体/转发消息]"), date = result.getLong("date"), isOutgoing = true, rawJson = result.toString(), entities = null, replyToJson = null, senderRole = null, senderTitle = null, isDeleted = false, isEdited = false, editHistory = null, reactions = null)
                    messageRepository.insertMessage(messageEntity); chatRepository.updateLastMessage(targetChatId, messageEntity.text ?: "", messageEntity.date * 1000); if (targetChatId == chatId) loadMessagesInternal(true) else withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "已转发", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private suspend fun deleteMessage(messageId: Long) {
        try {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return
            val res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/deleteMessage?chat_id=$chatId&message_id=$messageId").build()).execute()
            if (res.isSuccessful) { messageRepository.markMessageAsDeleted(messageId, chatId); withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "已撤回", Toast.LENGTH_SHORT).show(); loadMessagesInternal(false) } } else withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "撤回失败", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {}
    }

    private fun sendTextMessage(text: String, replyTo: Long?, replyMarkupStr: String?) {
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val jsonBody = JSONObject().apply { put("chat_id", chatId); put("text", text); put("parse_mode", "Markdown"); replyTo?.let { put("reply_to_message_id", it) }; replyMarkupStr?.let { put("reply_markup", JSONObject(it)) } }
            var res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendMessage").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()).execute()
            if (!res.isSuccessful) { jsonBody.remove("parse_mode"); res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendMessage").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()).execute() }
            if (res.isSuccessful) {
                val msg = JSONObject(res.body?.string() ?: "")
                if (msg.getBoolean("ok")) {
                    val result = msg.getJSONObject("result"); var replyToJson: String? = null; if (replyTo != null) { try { val repliedMsg = messageRepository.getMessages(chatId).find { it.messageId == replyTo }; replyToJson = repliedMsg?.rawJson } catch (_: Exception) {} }
                    val messageEntity = MessageEntity(messageId = result.getLong("message_id"), chatId = chatId, senderUserId = null, senderName = "我", text = text, date = result.getLong("date"), isOutgoing = true, rawJson = result.toString(), entities = null, replyToJson = replyToJson, senderRole = null, senderTitle = null, isDeleted = false, isEdited = false, editHistory = null, reactions = null); messageRepository.insertMessage(messageEntity); chatRepository.updateLastMessage(chatId, text, result.getLong("date") * 1000); loadMessagesInternal(true)
                }
            }
        }
    }

    private fun gotoCrash(throwable: Throwable) { startActivity(Intent(this, CrashActivity::class.java).apply { putExtra("stack_trace", android.util.Log.getStackTraceString(throwable)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }); finish() }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == android.R.id.home) { finish(); return true }; return super.onOptionsItemSelected(item) }
}
