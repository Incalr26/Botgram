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
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.incalr26.botgram.R
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.util.AvatarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        lifecycleScope.launch(Dispatchers.IO) { loadFullChatInfo() }
    }

    private suspend fun loadFullChatInfo() {
        val prefs = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("bot_token", "") ?: return
        
        try {
            val req = Request.Builder().url("https://api.telegram.org/bot$token/getChat?chat_id=$chatId").build()
            val res = ApiClient.getClient().newCall(req).execute()
            if (res.isSuccessful) {
                val json = JSONObject(res.body?.string() ?: "")
                if (json.getBoolean("ok")) {
                    val chat = json.getJSONObject("result")
                    withContext(Dispatchers.Main) { renderChatInfo(chat) }
                }
            }
        } catch (_: Exception) {}

        val avatarUrl = AvatarHelper.getUserAvatar(chatId)
        if (!avatarUrl.isNullOrEmpty()) {
            withContext(Dispatchers.Main) {
                findViewById<ImageView>(R.id.chatAvatar).load(avatarUrl) { crossfade(true) }
            }
        }
    }

    private fun renderChatInfo(chat: JSONObject) {
        val type = chat.getString("type")
        val title = if (type == "private") chat.optString("first_name", "") + " " + chat.optString("last_name", "") else chat.optString("title", "群组")
        findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar).title = title.trim()

        val username = chat.optString("username", "")
        
        if (type == "private") {
            if (username.isNotEmpty()) addInfoRow("用户名", "@$username")
            if (chat.has("bio")) addInfoRow("个人简介", chat.getString("bio"))
        } else {
            if (username.isNotEmpty()) addInfoRow("公开链接", "https://t.me/$username")
            if (chat.has("description")) addInfoRow("群组描述", chat.getString("description"))
        }

        addInfoRow("ID", chat.getLong("id").toString())
        addInfoRow("类型", when (type) { "private" -> "私聊"; "group" -> "普通群组"; "supergroup" -> "超级群组"; "channel" -> "频道"; else -> type })
    }

    private fun addInfoRow(title: String, content: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (24 * resources.displayMetrics.density).toInt() }
        }
        val tvText = TypedValue(); theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tvText, true)
        val tvHint = TypedValue(); theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tvHint, true)

        val titleView = TextView(this).apply { text = title; setTextColor(tvHint.data); textSize = 13f }
        val contentView = TextView(this).apply { text = content; setTextColor(tvText.data); textSize = 16f; setTextIsSelectable(true); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 } }
        
        row.addView(titleView); row.addView(contentView)
        findViewById<LinearLayout>(R.id.detailsLayout).addView(row)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
