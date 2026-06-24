package com.incalr26.botgram.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
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
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import com.incalr26.botgram.data.repository.MessageRepository
import kotlinx.coroutines.*
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ChatActivity : AppCompatActivity() {
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private var chatId: Long = 0
    private var replyToMessageId: Long? = null
    private var chatType: String = "private"

    private val pendingUploads = mutableListOf<Pair<Uri, String>>()
    private val crashHandler = CoroutineExceptionHandler { _, throwable -> gotoCrash(throwable) }

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handleMediaSelected(uris, "photo") }
    private val pickVideosLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handleMediaSelected(uris, "video") }
    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handleMediaSelected(uris, "document") }

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
                    sendButton.alpha = 1.0f
                } else {
                    sendButton.isEnabled = false
                    sendButton.setColorFilter(Color.parseColor("#9E9E9E"), PorterDuff.Mode.SRC_IN)
                    sendButton.alpha = 0.5f
                }
            }

            messageInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSendButtonState() }
                override fun afterTextChanged(s: Editable?) {}
            })

            attachButton.setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (imm.isActive(messageInput)) {
                    imm.hideSoftInputFromWindow(messageInput.windowToken, 0)
                    attachButton.postDelayed({ showAttachMenu(attachButton) }, 250)
                } else {
                    showAttachMenu(attachButton)
                }
            }

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
                loadTitleAndType()
                loadMessagesInternal()
                chatRepository.updateUnreadCount(chatId, 0)
            }
        } catch (e: Exception) { gotoCrash(e) }
    }

    private fun handleMediaSelected(uris: List<Uri>?, type: String) {
        if (uris.isNullOrEmpty()) return
        if (pendingUploads.isNotEmpty() && pendingUploads.first().second != type) {
            Toast.makeText(this, "暂不支持混搭不同类型的媒体", Toast.LENGTH_SHORT).show()
            pendingUploads.clear()
        }
        uris.forEach { pendingUploads.add(Pair(it, type)) }
        updatePreviewUI()
        findViewById<ImageButton>(R.id.sendButton).apply { isEnabled = true; clearColorFilter(); alpha = 1.0f }
    }

    private fun updatePreviewUI() {
        val previewContainer = findViewById<LinearLayout>(R.id.mediaPreviewContainer)
        val previewList = findViewById<LinearLayout>(R.id.previewList)
        if (pendingUploads.isEmpty()) { previewContainer.visibility = View.GONE; return }
        previewContainer.visibility = View.VISIBLE
        previewList.removeAllViews()
        val density = resources.displayMetrics.density
        for ((index, item) in pendingUploads.withIndex()) {
            val frame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams((64 * density).toInt(), (64 * density).toInt()).apply { marginEnd = (8 * density).toInt() } }
            val card = MaterialCardView(this).apply {
                layoutParams = FrameLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt()).apply { gravity = Gravity.BOTTOM or Gravity.START }
                radius = 8 * density
                setCardBackgroundColor(Color.parseColor("#E0E0E0"))
                cardElevation = 0f
            }
            val icon = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1); scaleType = ImageView.ScaleType.CENTER_CROP
                when (item.second) {
                    "photo" -> setImageURI(item.first)
                    "video" -> { setImageResource(android.R.drawable.presence_video_online); setPadding(24, 24, 24, 24) }
                    else -> { setImageResource(android.R.drawable.ic_menu_agenda); setPadding(24, 24, 24, 24); setColorFilter(Color.GRAY) }
                }
            }
            card.addView(icon)
            val closeBtn = ImageButton(this).apply {
                layoutParams = FrameLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()).apply { gravity = Gravity.TOP or Gravity.END }
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.WHITE); setPadding(8, 8, 8, 8); scaleType = ImageView.ScaleType.FIT_CENTER
                setOnClickListener { pendingUploads.removeAt(index); updatePreviewUI() }
            }
            frame.addView(card); frame.addView(closeBtn); previewList.addView(frame)
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
            Triple("发送文件", android.R.drawable.ic_menu_agenda, 3)
        )
        val popupWindow = PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply { isFocusable = true; setBackgroundDrawable(null) }

        items.forEach { (title, iconRes, action) ->
            val itemView = layoutInflater.inflate(R.layout.item_popup_menu, container, false)
            itemView.findViewById<ImageView>(R.id.menu_icon).apply { setImageResource(iconRes); setColorFilter(primaryColor) }
            itemView.findViewById<TextView>(R.id.menu_text).apply { text = title; setTextColor(textColor) }
            itemView.setOnClickListener { popupWindow.dismiss(); when (action) { 1 -> pickImagesLauncher.launch("image/*"); 2 -> pickVideosLauncher.launch("video/*"); 3 -> pickFilesLauncher.launch("*/*") } }
            container.addView(itemView)
        }
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val anchorLocation = IntArray(2); anchor.getLocationOnScreen(anchorLocation)
        popupWindow.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, anchorLocation[0], anchorLocation[1] - container.measuredHeight - (16 * resources.displayMetrics.density).toInt())
    }

    private fun uploadMediaAndSend(caption: String) {
        val uploads = pendingUploads.toList()
        lifecycleScope.launch(Dispatchers.IO + crashHandler) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: return@launch
            val client = ApiClient.getClient().newBuilder().connectTimeout(30, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
            
            uploads.forEach { (uri, type) ->
                val file = File(cacheDir, "temp_upload_${System.currentTimeMillis()}")
                contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart(if (type == "photo") "photo" else "document", file.name, file.asRequestBody("*/*".toMediaTypeOrNull()))
                if (caption.isNotEmpty()) builder.addFormDataPart("caption", caption)
                
                val url = "https://api.telegram.org/bot$token/send${type.replaceFirstChar { it.uppercase() }}"
                val request = Request.Builder().url(url).post(builder.build()).build()
                client.newCall(request).execute()
            }
        }
    }

    private suspend fun loadTitleAndType() {
        val chat = chatRepository.getChatById(chatId) ?: return
        var memberCount: Int? = null
        try {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: ""
            val countReq = Request.Builder().url("https://api.telegram.org/bot$token/getChatMemberCount?chat_id=$chatId").build()
            val countRes = ApiClient.getClient().newCall(countReq).execute()
            if (countRes.isSuccessful) { val json = JSONObject(countRes.body?.string() ?: ""); if (json.getBoolean("ok")) memberCount = json.getInt("result") }
        } catch (_: Exception) {}

        withContext(Dispatchers.Main) {
            chatType = chat.type
            supportActionBar?.title = if (chat.type == "private") chat.firstName ?: chat.username ?: "私聊" else chat.title ?: "群组"
            val typeStr = when (chat.type) { "private" -> "私聊"; "group" -> "群组"; "supergroup" -> "超级群组"; "channel" -> "频道"; else -> chat.type }
            supportActionBar?.subtitle = typeStr + (if (memberCount != null && memberCount > 0) " $memberCount 位成员" else "")
        }
    }

    private fun showMessageMenu(message: MessageEntity, anchor: View) {
        val items = arrayOf("回复", "复制文本", "保存媒体")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when(which) {
                    0 -> { replyToMessageId = message.messageId; findViewById<LinearLayout>(R.id.replyBanner).visibility = View.VISIBLE; findViewById<TextView>(R.id.replyText).text = "回复: ${message.text?.take(20)}" }
                    1 -> { val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager; cm.setPrimaryClip(android.content.ClipData.newPlainText("text", message.text)); Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show() }
                    2 -> { Toast.makeText(this, "保存功能已调用", Toast.LENGTH_SHORT).show() }
                }
            }.show()
    }

    private fun sendTextMessage(text: String, replyId: Long?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE).getString("bot_token", "") ?: ""
            var url = "https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=${java.net.URLEncoder.encode(text, "UTF-8")}"
            if (replyId != null) url += "&reply_to_message_id=$replyId"
            ApiClient.getClient().newCall(Request.Builder().url(url).build()).execute()
        }
    }

    private suspend fun loadMessagesInternal() {
        val msgs = messageRepository.getMessages(chatId)
        withContext(Dispatchers.Main) { adapter.submitList(msgs) }
    }

    private fun getStatusBarHeight(): Int { val res = resources.getIdentifier("status_bar_height", "dimen", "android"); return if (res > 0) resources.getDimensionPixelSize(res) else 0 }
    
    private fun gotoCrash(e: Throwable) {
        Log.e("ChatActivity", "Crash", e)
        finish()
    }
}
