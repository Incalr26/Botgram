package com.incalr26.botgram.ui.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.card.MaterialCardView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.util.AvatarHelper
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject

class ChatInfoActivity : AppCompatActivity() {

    private var chatId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat_info)

        chatId = intent.getLongExtra("chatId", 0)
        if (chatId == 0L) { finish(); return }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        val miniAvatar = findViewById<ImageView>(R.id.miniToolbarAvatar)
        miniAvatar.setOnClickListener { appBarLayout.setExpanded(true, true) }

        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            if (Math.abs(verticalOffset) >= appBar.totalScrollRange - 50) miniAvatar.visibility = View.VISIBLE 
            else miniAvatar.visibility = View.GONE
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val token = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE).getString("bot_token", "") ?: ""
            val req = Request.Builder().url("https://api.telegram.org/bot$token/getChat?chat_id=$chatId").build()
            try {
                val res = ApiClient.getClient().newCall(req).execute()
                if (res.isSuccessful) {
                    val json = JSONObject(res.body?.string() ?: "")
                    if (json.getBoolean("ok")) withContext(Dispatchers.Main) { renderUI(json.getJSONObject("result")) }
                }
            } catch (e: Exception) {}
            
            val avatarUrl = AvatarHelper.getUserAvatar(chatId)
            if (!avatarUrl.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    findViewById<ImageView>(R.id.chatInfoAvatar).load(avatarUrl) { crossfade(true) }
                    miniAvatar.load(avatarUrl) { crossfade(true); transformations(CircleCropTransformation()) }
                }
            }
        }
    }

    private fun renderUI(chat: JSONObject) {
        val title = chat.optString("title").takeIf { it.isNotEmpty() } ?: chat.optString("first_name") + " " + chat.optString("last_name")
        findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar).title = title.trim()

        val container = findViewById<LinearLayout>(R.id.infoContentContainer)
        
        fun addCard(title: String, contentBlocks: List<Pair<String, String>>) {
            if (contentBlocks.isEmpty()) return
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 32 }
                radius = 24f; cardElevation = 0f; setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant))
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
        val username = chat.optString("username")
        basicInfo.add("类型" to when (type) { "private" -> "私聊"; "group" -> "私密群组"; "supergroup" -> if (username.isNotEmpty()) "公开超级群组" else "私密超级群组"; "channel" -> if (username.isNotEmpty()) "公开频道" else "私密频道"; else -> type })
        if (username.isNotEmpty()) {
            if (type == "private") basicInfo.add("用户名" to "@$username") else basicInfo.add("公开链接" to "https://t.me/$username")
        }
        addCard("基本信息", basicInfo)

        val descInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("bio")) descInfo.add("简介" to chat.getString("bio"))
        if (chat.has("description")) descInfo.add("简介" to chat.getString("description"))
        if (chat.has("member_count")) descInfo.add("成员总数" to "${chat.getInt("member_count")} 人")
        if (chat.has("invite_link")) descInfo.add("专属邀请链接" to chat.getString("invite_link"))
        if (chat.has("linked_chat_id")) descInfo.add("关联讨论区 ID" to chat.getLong("linked_chat_id").toString())
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
            pMap.forEach { (key, cnName) -> if (permObj.has(key)) permList.add("$cnName\n($key)" to if (permObj.getBoolean(key)) "允许" else "禁止") }
            addCard("成员权限设置", permList)
        }

        val extraInfo = mutableListOf<Pair<String, String>>()
        if (chat.has("pinned_message")) extraInfo.add("当前置顶消息" to chat.getJSONObject("pinned_message").optString("text", "[媒体/复杂消息]"))
        if (chat.has("message_auto_delete_time")) extraInfo.add("消息自动删除" to "${chat.getInt("message_auto_delete_time") / 3600} 小时后")
        if (chat.has("slow_mode_delay")) extraInfo.add("慢速模式延迟" to "${chat.getInt("slow_mode_delay")} 秒")
        if (chat.has("has_hidden_members") && chat.getBoolean("has_hidden_members")) extraInfo.add("隐私设置" to "群成员已被隐藏")
        if (chat.has("has_protected_content") && chat.getBoolean("has_protected_content")) extraInfo.add("内容保护" to "已开启 (禁止转发与保存)")
        if (chat.has("join_to_send_messages") && chat.getBoolean("join_to_send_messages")) extraInfo.add("发言限制" to "必须加入群组才能发言")
        if (chat.has("join_by_request") && chat.getBoolean("join_by_request")) extraInfo.add("进群方式" to "需要管理员审批")
        
        if (chat.has("available_reactions")) {
            val arr = chat.getJSONArray("available_reactions"); val reactions = mutableListOf<String>()
            for (i in 0 until arr.length()) { val r = arr.getJSONObject(i); if (r.optString("type") == "emoji") reactions.add(r.getString("emoji")) }
            if (reactions.isNotEmpty()) extraInfo.add("可用表情回应" to reactions.joinToString(" "))
        }
        addCard("系统与高级设置", extraInfo)
    }

    private fun getColorAttr(attr: Int): Int { val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == android.R.id.home) { finish(); return true }; return super.onOptionsItemSelected(item) }
}
