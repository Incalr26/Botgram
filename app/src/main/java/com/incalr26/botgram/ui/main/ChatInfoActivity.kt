package com.incalr26.botgram.ui.main

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
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
        window.statusBarColor = Color.TRANSPARENT

        chatId = intent.getLongExtra("chatId", 0)
        if (chatId == 0L) { finish(); return }
        botToken = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE).getString("bot_token", "") ?: ""

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        val collapsingToolbar = findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)
        val miniContent = findViewById<View>(R.id.miniToolbarContent)
        val chatInfoAvatar = findViewById<ImageView>(R.id.chatInfoAvatar)
        
        collapsingToolbar.setContentScrimColor(getColorAttr(com.google.android.material.R.attr.colorSurface))

        miniContent.setOnClickListener {
            appBarLayout.setExpanded(true, true)
            findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView).smoothScrollTo(0,0)
        }

        var startY = 0f
        appBarLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    if (appBarLayout.bottom >= appBarLayout.height && (event.y - startY) > 250f) {
                        if (!currentAvatarUrl.isNullOrEmpty()) {
                            val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                            val iv = ImageView(this).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); scaleType = ImageView.ScaleType.FIT_CENTER; load(currentAvatarUrl); setOnClickListener { dialog.dismiss() } }
                            dialog.setContentView(iv); dialog.show()
                        }
                        startY = event.y 
                    }
                }
            }
            false
        }

        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            if (Math.abs(verticalOffset) >= appBar.totalScrollRange - 50) miniContent.visibility = View.VISIBLE 
            else miniContent.visibility = View.GONE
        }

        lifecycleScope.launch(Dispatchers.Main) {
            checkBotAdminStatus()
            val resultJson = withContext(Dispatchers.IO) {
                try { val res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$botToken/getChat?chat_id=$chatId").build()).execute(); if (res.isSuccessful) JSONObject(res.body?.string() ?: "") else null } catch (e: Exception) { null }
            }

            if (resultJson != null && resultJson.getBoolean("ok")) renderUI(resultJson.getJSONObject("result"))
            
            val avatarUrl = withContext(Dispatchers.IO) { AvatarHelper.getUserAvatar(chatId) }
            if (!avatarUrl.isNullOrEmpty()) {
                currentAvatarUrl = avatarUrl
                chatInfoAvatar.load(avatarUrl) { crossfade(true) }
                findViewById<ImageView>(R.id.miniToolbarAvatar).load(avatarUrl) { crossfade(true) }
            }
        }
    }

    private suspend fun checkBotAdminStatus() {
        if (chatId > 0) return
        withContext(Dispatchers.IO) {
            try {
                val res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$botToken/getMe").build()).execute()
                if (res.isSuccessful) {
                    val botId = JSONObject(res.body?.string() ?: "").getJSONObject("result").getLong("id")
                    val checkRes = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$botToken/getChatMember?chat_id=$chatId&user_id=$botId").build()).execute()
                    if (checkRes.isSuccessful) {
                        val status = JSONObject(checkRes.body?.string() ?: "").getJSONObject("result").optString("status", "")
                        isBotAdmin = (status == "administrator" || status == "creator")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun renderUI(chat: JSONObject) {
        val titleStr = chat.optString("title").takeIf { it.isNotEmpty() } ?: (chat.optString("first_name") + " " + chat.optString("last_name"))
        findViewById<TextView>(R.id.miniToolbarTitle).text = titleStr.trim()

        val container = findViewById<LinearLayout>(R.id.infoContentContainer)
        container.removeAllViews()
        
        fun addCard(title: String, blocks: List<Pair<String, String>>) {
            if (blocks.isEmpty()) return
            val card = MaterialCardView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 32 }; radius = 24f; cardElevation = 0f; setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurface)) }
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
            layout.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorPrimary)); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 24) })
            blocks.forEach { (l, v) ->
                layout.addView(TextView(this).apply { text = l; textSize = 12f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)) })
                layout.addView(TextView(this).apply { text = v; textSize = 16f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurface)); setPadding(0, 4, 0, 24); setTextIsSelectable(true) })
            }
            card.addView(layout); container.addView(card)
        }

        // 基本信息
        val type = chat.getString("type")
        val username = chat.optString("username")
        val basicInfo = mutableListOf<Pair<String, String>>()
        basicInfo.add("ID" to chat.getLong("id").toString())
        basicInfo.add("类型" to when (type) { "private" -> "私聊"; "group" -> "私密群组"; "supergroup" -> if (username.isNotEmpty()) "公开群组" else "私密群组"; "channel" -> if (username.isNotEmpty()) "公开频道" else "私密频道"; else -> type })
        if (username.isNotEmpty()) basicInfo.add(if (type == "private") "用户名" else "公开链接" to if (type == "private") "@$username" else "https://t.me/$username")
        addCard("基本信息", basicInfo)

        // 详细资料（提到权限管理之上）
        val descInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("bio")) descInfo.add("简介" to chat.getString("bio"))
        if (chat.has("description")) descInfo.add("简介" to chat.getString("description"))
        if (chat.has("member_count")) descInfo.add("成员数" to "${chat.getInt("member_count")} 人")
        if (type != "private") {
            if (chat.has("invite_link")) descInfo.add("邀请链接" to chat.getString("invite_link"))
            if (chat.has("linked_chat_id")) descInfo.add("关联讨论组 ID" to chat.getLong("linked_chat_id").toString())
        }
        addCard("详细资料", descInfo)

        // 权限管理
        if (type != "private" && chat.has("permissions")) {
            val permObj = chat.getJSONObject("permissions")
            val card = MaterialCardView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 32 }; radius = 24f; cardElevation = 0f; setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurface)) }
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
            layout.addView(TextView(this).apply { text = "权限管理"; textSize = 14f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorPrimary)); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 24) })
            mapOf("can_send_messages" to "发送消息", "can_send_audios" to "发送音频", "can_send_documents" to "发送文件", "can_send_photos" to "发送图片", "can_send_videos" to "发送视频", "can_send_polls" to "发起投票", "can_change_info" to "修改群资料", "can_invite_users" to "邀请成员", "can_pin_messages" to "置顶消息").forEach { (key, cnName) ->
                if (permObj.has(key)) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 12) }
                    val txtLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                    txtLayout.addView(TextView(this).apply { text = cnName; textSize = 16f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurface)) })
                    val sw = MaterialSwitch(this@ChatInfoActivity).apply { 
                        isChecked = permObj.getBoolean(key); isEnabled = isBotAdmin
                        setOnCheckedChangeListener { _, checked ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val apiParams = JSONObject().apply { put("chat_id", chatId); put("permissions", permObj.put(key, checked)) }
                                    val res = ApiClient.getClient().newCall(Request.Builder().url("https://api.telegram.org/bot$botToken/setChatPermissions").post(apiParams.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()).execute()
                                    withContext(Dispatchers.Main) { if (!res.isSuccessful || !JSONObject(res.body?.string() ?: "").optBoolean("ok")) { Toast.makeText(this@ChatInfoActivity, "修改失败", Toast.LENGTH_SHORT).show(); isChecked = !checked } }
                                } catch (e: Exception) { withContext(Dispatchers.Main) { isChecked = !checked } }
                            }
                        }
                    }
                    row.addView(txtLayout); row.addView(sw); layout.addView(row)
                }
            }
            card.addView(layout); container.addView(card)
        }

        // 高级设置
        val extraInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("message_auto_delete_time")) extraInfo.add("自动删除时间" to "${chat.getInt("message_auto_delete_time")} 秒")
        if (type != "private") {
            if (chat.has("pinned_message")) extraInfo.add("当前置顶消息" to chat.getJSONObject("pinned_message").optString("text", "[媒体消息]"))
            if (chat.has("slow_mode_delay")) extraInfo.add("慢速模式延迟" to "${chat.getInt("slow_mode_delay")} 秒")
            if (chat.optBoolean("has_protected_content", false)) extraInfo.add("禁止转发与保存" to "已开启")
            if (chat.optBoolean("has_hidden_members", false)) extraInfo.add("隐藏群成员" to "已开启")
            if (chat.optBoolean("join_to_send_messages", false)) extraInfo.add("发言限制" to "必须加入才能发言")
            if (chat.has("join_by_request")) extraInfo.add("通过链接进群" to if (chat.getBoolean("join_by_request")) "需管理员审批" else "免审批直接加入")
            if (chat.has("available_reactions")) {
                val arr = chat.getJSONArray("available_reactions"); val reactions = mutableListOf<String>()
                for (i in 0 until arr.length()) { val r = arr.getJSONObject(i); if (r.optString("type") == "emoji") reactions.add(r.getString("emoji")) }
                if (reactions.isNotEmpty()) extraInfo.add("可用表情" to reactions.joinToString(" "))
            }
        }
        addCard("系统与设置", extraInfo)
    }
    
    private fun Context.getColorAttr(attr: Int): Int { val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == android.R.id.home) { finish(); return true }; return super.onOptionsItemSelected(item) }
}
