package com.incalr26.botgram.ui.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.card.MaterialCardView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.util.AvatarHelper
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ChatInfoActivity : AppCompatActivity() {

    private var chatId: Long = 0
    private var isBotAdmin: Boolean = false
    private var botToken: String = ""
    private var currentAvatarUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat_info)

        chatId = intent.getLongExtra("chatId", 0)
        if (chatId == 0L) { finish(); return }

        botToken = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE).getString("bot_token", "") ?: ""

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        window.statusBarColor = Color.TRANSPARENT

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        val miniAvatar = findViewById<ImageView>(R.id.miniToolbarAvatar)
        miniAvatar.setOnClickListener { appBarLayout.setExpanded(true, true) }
        
        findViewById<ImageView>(R.id.chatInfoAvatar).setOnClickListener {
            if (!currentAvatarUrl.isNullOrEmpty()) {
                val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                val iv = ImageView(this).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    load(currentAvatarUrl)
                    setOnClickListener { dialog.dismiss() }
                }
                dialog.setContentView(iv); dialog.show()
            }
        }

        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            if (Math.abs(verticalOffset) >= appBar.totalScrollRange - 50) miniAvatar.visibility = View.VISIBLE 
            else miniAvatar.visibility = View.GONE
        }

        lifecycleScope.launch(Dispatchers.Main) {
            checkBotAdminStatus()
            val resultJson = withContext(Dispatchers.IO) {
                val req = Request.Builder().url("https://api.telegram.org/bot$botToken/getChat?chat_id=$chatId").build()
                try { val res = ApiClient.getClient().newCall(req).execute(); if (res.isSuccessful) JSONObject(res.body?.string() ?: "") else null } catch (e: Exception) { null }
            }

            if (resultJson != null && resultJson.getBoolean("ok")) renderUI(resultJson.getJSONObject("result"))
            else {
                val dbHelper = com.incalr26.botgram.BotApp.instance.databaseHelper
                val localChat = com.incalr26.botgram.data.repository.ChatRepository(dbHelper).getChatById(chatId)
                if (localChat != null) {
                    val fallback = JSONObject().apply { put("id", localChat.chatId); put("type", localChat.type); put("title", localChat.title ?: ""); put("first_name", localChat.firstName ?: ""); put("last_name", localChat.lastName ?: ""); put("username", localChat.username ?: "") }
                    renderUI(fallback)
                }
            }
            
            val avatarUrl = withContext(Dispatchers.IO) { AvatarHelper.getUserAvatar(chatId) }
            if (!avatarUrl.isNullOrEmpty()) {
                currentAvatarUrl = avatarUrl
                findViewById<ImageView>(R.id.chatInfoAvatar).load(avatarUrl) { crossfade(true) }
                miniAvatar.load(avatarUrl) { crossfade(true); transformations(CircleCropTransformation()) }
            }
        }
    }

    private suspend fun checkBotAdminStatus() {
        if (chatId > 0) return
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("https://api.telegram.org/bot$botToken/getMe").build()
                val res = ApiClient.getClient().newCall(req).execute()
                if (res.isSuccessful) {
                    val botId = JSONObject(res.body?.string() ?: "").getJSONObject("result").getLong("id")
                    val checkReq = Request.Builder().url("https://api.telegram.org/bot$botToken/getChatMember?chat_id=$chatId&user_id=$botId").build()
                    val checkRes = ApiClient.getClient().newCall(checkReq).execute()
                    if (checkRes.isSuccessful) {
                        val status = JSONObject(checkRes.body?.string() ?: "").getJSONObject("result").optString("status", "")
                        isBotAdmin = (status == "administrator" || status == "creator")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun renderUI(chat: JSONObject) {
        val title = chat.optString("title").takeIf { it.isNotEmpty() } ?: (chat.optString("first_name") + " " + chat.optString("last_name"))
        findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar).title = title.trim()

        val container = findViewById<LinearLayout>(R.id.infoContentContainer)
        container.removeAllViews()
        
        fun addCard(titleStr: String, contentBlocks: List<Pair<String, String>>) {
            if (contentBlocks.isEmpty()) return
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 32 }
                radius = 24f; cardElevation = 0f; setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
            val sectionTitle = TextView(this).apply { text = titleStr; textSize = 14f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorPrimary)); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 24) }
            layout.addView(sectionTitle)
            
            contentBlocks.forEach { (label, value) ->
                val tvLabel = TextView(this).apply { text = label; textSize = 12f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)) }
                val tvValue = TextView(this).apply { text = value; textSize = 16f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurface)); setPadding(0, 4, 0, 24); setTextIsSelectable(true) }
                layout.addView(tvLabel); layout.addView(tvValue)
            }
            card.addView(layout); container.addView(card)
        }

        fun addPermissionCard(permObj: JSONObject) {
            val card = MaterialCardView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 32 }; radius = 24f; cardElevation = 0f; setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant)) }
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
            layout.addView(TextView(this).apply { text = "群组权限管理"; textSize = 14f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorPrimary)); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 24) })
            
            val pMap = mapOf(
                "can_send_messages" to "发送消息", "can_send_audios" to "发送音频", "can_send_documents" to "发送文件",
                "can_send_photos" to "发送图片", "can_send_videos" to "发送视频", "can_send_polls" to "发起投票",
                "can_change_info" to "修改群资料", "can_invite_users" to "邀请成员", "can_pin_messages" to "置顶消息"
            )
            pMap.forEach { (key, cnName) ->
                if (permObj.has(key)) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 12) }
                    val txtLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                    txtLayout.addView(TextView(this).apply { text = cnName; textSize = 16f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurface)) })
                    txtLayout.addView(TextView(this).apply { text = key; textSize = 11f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)) })
                    
                    val sw = SwitchCompat(this).apply { 
                        isChecked = permObj.getBoolean(key); isEnabled = isBotAdmin
                        setOnCheckedChangeListener { _, checked ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val currentPerms = permObj; currentPerms.put(key, checked)
                                    val apiParams = JSONObject().apply { put("chat_id", chatId); put("permissions", currentPerms) }
                                    val req = Request.Builder().url("https://api.telegram.org/bot$botToken/setChatPermissions").post(apiParams.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
                                    val res = ApiClient.getClient().newCall(req).execute()
                                    withContext(Dispatchers.Main) { if (!(res.isSuccessful && JSONObject(res.body?.string() ?: "").optBoolean("ok", false))) { Toast.makeText(this@ChatInfoActivity, "权限不足", Toast.LENGTH_SHORT).show(); isChecked = !checked } }
                                } catch (e: Exception) { withContext(Dispatchers.Main) { isChecked = !checked } }
                            }
                        }
                    }
                    row.addView(txtLayout); row.addView(sw); layout.addView(row)
                }
            }
            card.addView(layout); container.addView(card)
        }

        fun addAdminListCard() {
            if (chatId > 0) return 
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url("https://api.telegram.org/bot$botToken/getChatAdministrators?chat_id=$chatId").build()
                    val res = ApiClient.getClient().newCall(req).execute()
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string() ?: "")
                        if (json.getBoolean("ok")) {
                            val arr = json.getJSONArray("result")
                            val admins = mutableListOf<Pair<String, String>>()
                            for (i in 0 until arr.length()) {
                                val user = arr.getJSONObject(i).getJSONObject("user")
                                val name = user.optString("first_name", "") + " " + user.optString("last_name", "")
                                val status = arr.getJSONObject(i).optString("status", "")
                                admins.add(name.trim() to (if (status == "creator") "创建者" else "管理员"))
                            }
                            withContext(Dispatchers.Main) { addCard("管理员列表", admins) }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val type = chat.getString("type")
        val username = chat.optString("username")
        val basicInfo = mutableListOf<Pair<String, String>>()
        basicInfo.add("ID" to chat.getLong("id").toString())
        basicInfo.add("类型" to when (type) { "private" -> "私聊"; "group" -> "私密群组"; "supergroup" -> if (username.isNotEmpty()) "公开群组" else "私密群组"; "channel" -> if (username.isNotEmpty()) "公开频道" else "私密频道"; else -> type })
        if (username.isNotEmpty()) {
            if (type == "private") basicInfo.add("用户名" to "@$username") else basicInfo.add("公开链接" to "https://t.me/$username")
        }
        addCard("基本信息", basicInfo)

        val descInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("bio")) descInfo.add("简介" to chat.getString("bio"))
        if (chat.has("description")) descInfo.add("简介" to chat.getString("description"))
        if (chat.has("member_count")) descInfo.add("成员数" to "${chat.getInt("member_count")} 人")
        
        if (type != "private") {
            if (chat.has("invite_link")) descInfo.add("邀请链接" to chat.getString("invite_link"))
            if (chat.has("linked_chat_id")) descInfo.add("关联讨论组 ID" to chat.getLong("linked_chat_id").toString())
            val permObj = chat.optJSONObject("permissions")
            if (permObj != null) addPermissionCard(permObj)
        }
        addCard("详细资料", descInfo)

        val extraInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("message_auto_delete_time")) extraInfo.add("自动删除时间" to "${chat.getInt("message_auto_delete_time")} 秒")
        
        if (type != "private") {
            if (chat.has("pinned_message")) extraInfo.add("当前置顶消息" to chat.getJSONObject("pinned_message").optString("text", "[媒体消息]"))
            if (chat.has("slow_mode_delay")) extraInfo.add("慢速模式延迟" to "${chat.getInt("slow_mode_delay")} 秒")
            if (chat.has("has_protected_content") && chat.getBoolean("has_protected_content")) extraInfo.add("禁止转发与保存" to "已开启")
            if (chat.has("has_hidden_members") && chat.getBoolean("has_hidden_members")) extraInfo.add("隐藏群成员" to "已开启")
            if (chat.has("join_to_send_messages") && chat.getBoolean("join_to_send_messages")) extraInfo.add("发言限制" to "必须加入群组才能发言")
            if (chat.has("join_by_request") && chat.getBoolean("join_by_request")) extraInfo.add("进群方式" to "需要管理员审批")
            if (chat.has("available_reactions")) {
                val arr = chat.getJSONArray("available_reactions"); val reactions = mutableListOf<String>()
                for (i in 0 until arr.length()) { val r = arr.getJSONObject(i); if (r.optString("type") == "emoji") reactions.add(r.getString("emoji")) }
                if (reactions.isNotEmpty()) extraInfo.add("可用表情回应" to reactions.joinToString(" "))
            }
        }
        addCard("系统与设置", extraInfo)
        
        addAdminListCard()
    }

    private fun Context.getColorAttr(attr: Int): Int { val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == android.R.id.home) { finish(); return true }; return super.onOptionsItemSelected(item) }
}
