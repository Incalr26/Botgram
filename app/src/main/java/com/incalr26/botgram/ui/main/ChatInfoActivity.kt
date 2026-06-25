package com.incalr26.botgram.ui.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import coil.load
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.card.MaterialCardView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.util.AvatarHelper
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject

class ChatInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat_info)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val chatId = intent.getLongExtra("chatId", 0L)
        if (chatId == 0L) { finish(); return }

        CoroutineScope(Dispatchers.IO).launch {
            val token = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE).getString("bot_token", "") ?: ""
            val req = Request.Builder().url("https://api.telegram.org/bot$token/getChat?chat_id=$chatId").build()
            try {
                val res = ApiClient.getClient().newCall(req).execute()
                if (res.isSuccessful) {
                    val json = JSONObject(res.body?.string() ?: "")
                    if (json.getBoolean("ok")) {
                        withContext(Dispatchers.Main) { renderUI(json.getJSONObject("result"), token) }
                    }
                }
            } catch (e: Exception) {}
            
            val avatarUrl = AvatarHelper.getUserAvatar(chatId)
            if (!avatarUrl.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    findViewById<ImageView>(R.id.chatInfoAvatar).load(avatarUrl) { crossfade(true) }
                }
            }
        }
    }

    private fun renderUI(chat: JSONObject, token: String) {
        val title = chat.optString("title").takeIf { it.isNotEmpty() } ?: chat.optString("first_name") + " " + chat.optString("last_name")
        findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar).title = title

        val container = findViewById<LinearLayout>(R.id.infoContentContainer)
        
        fun addCard(title: String, contentBlocks: List<Pair<String, String>>) {
            if (contentBlocks.isEmpty()) return
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 32 }
                radius = 24f; cardElevation = 0f
                setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
            
            val sectionTitle = TextView(this).apply { text = title; textSize = 14f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorPrimary)); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 24) }
            layout.addView(sectionTitle)
            
            contentBlocks.forEach { (label, value) ->
                val tvLabel = TextView(this).apply { text = label; textSize = 12f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)) }
                val tvValue = TextView(this).apply { text = value; textSize = 16f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurface)); setPadding(0, 4, 0, 24); setTextIsSelectable(true) }
                layout.addView(tvLabel); layout.addView(tvValue)
            }
            card.addView(layout); container.addView(card)
        }

        val basicInfo = mutableListOf<Pair<String, String>>()
        basicInfo.add("Chat ID" to chat.getLong("id").toString())
        val type = chat.getString("type")
        basicInfo.add("类型" to when (type) { "private" -> "私聊"; "group" -> "普通群组"; "supergroup" -> "超级群组"; "channel" -> "频道"; else -> type })
        val username = chat.optString("username")
        if (username.isNotEmpty()) {
            if (type == "private") basicInfo.add("用户名" to "@$username")
            else basicInfo.add("公开链接" to "https://t.me/$username")
        }
        addCard("基本信息", basicInfo)

        val descInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("bio")) descInfo.add("个人简介" to chat.getString("bio"))
        if (chat.has("description")) descInfo.add("群组/频道描述" to chat.getString("description"))
        if (chat.has("member_count")) descInfo.add("成员总数" to "${chat.getInt("member_count")} 人")
        if (chat.has("linked_chat_id")) descInfo.add("关联群组 ID" to chat.getLong("linked_chat_id").toString())
        addCard("详细资料", descInfo)

        val permObj = chat.optJSONObject("permissions")
        if (permObj != null) {
            val permList = mutableListOf<Pair<String, String>>()
            val pMap = mapOf(
                "can_send_messages" to "发送消息", "can_send_audios" to "发送音频", "can_send_documents" to "发送文件",
                "can_send_photos" to "发送图片", "can_send_videos" to "发送视频", "can_send_video_notes" to "发送视频留言",
                "can_send_voice_notes" to "发送语音", "can_send_polls" to "发送投票", "can_send_other_messages" to "发送其他消息",
                "can_add_web_page_previews" to "添加网页预览", "can_change_info" to "修改群信息", "can_invite_users" to "邀请用户",
                "can_pin_messages" to "置顶消息", "can_manage_topics" to "管理话题"
            )
            pMap.forEach { (key, cnName) ->
                if (permObj.has(key)) {
                    val allow = if (permObj.getBoolean(key)) "允许" else "禁止"
                    permList.add("$cnName\n($key)" to allow)
                }
            }
            addCard("成员权限", permList)
        }

        val extraInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("slow_mode_delay")) extraInfo.add("慢速模式延迟" to "${chat.getInt("slow_mode_delay")} 秒")
        if (chat.has("has_protected_content") && chat.getBoolean("has_protected_content")) extraInfo.add("内容保护" to "已开启 (禁止转发与保存)")
        if (chat.has("join_to_send_messages") && chat.getBoolean("join_to_send_messages")) extraInfo.add("发言限制" to "必须加入群组才能发言")
        if (chat.has("join_by_request") && chat.getBoolean("join_by_request")) extraInfo.add("进群方式" to "需要管理员审批")
        addCard("额外设置", extraInfo)
    }

    private fun getColorAttr(attr: Int): Int { val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == android.R.id.home) { finish(); return true }; return super.onOptionsItemSelected(item) }
}
