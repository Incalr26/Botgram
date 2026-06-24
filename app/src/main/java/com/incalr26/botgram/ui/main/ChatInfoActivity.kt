package com.incalr26.botgram.ui.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import coil.Coil
import coil.request.ImageRequest
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.card.MaterialCardView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.remote.ApiClient
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
        }
    }

    private fun renderUI(chat: JSONObject, token: String) {
        val title = chat.optString("title").takeIf { it.isNotEmpty() } ?: chat.optString("first_name") + " " + chat.optString("last_name")
        findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar).title = title

        val photoObj = chat.optJSONObject("photo")
        if (photoObj != null) {
            val fileId = photoObj.optString("big_file_id")
            CoroutineScope(Dispatchers.IO).launch {
                val pReq = Request.Builder().url("https://api.telegram.org/bot$token/getFile?file_id=$fileId").build()
                try {
                    val pRes = ApiClient.getClient().newCall(pReq).execute()
                    if (pRes.isSuccessful) {
                        val path = JSONObject(pRes.body?.string() ?: "").getJSONObject("result").getString("file_path")
                        val imgUrl = "https://api.telegram.org/file/bot$token/$path"
                        withContext(Dispatchers.Main) {
                            val imgView = findViewById<ImageView>(R.id.chatInfoAvatar)
                            Coil.imageLoader(this@ChatInfoActivity).enqueue(ImageRequest.Builder(this@ChatInfoActivity).data(imgUrl).target(imgView).build())
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        val container = findViewById<LinearLayout>(R.id.infoContentContainer)
        fun addCard(label: String, value: String) {
            if (value.isBlank() || value == "null") return
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
                radius = 24f; cardElevation = 0f
                setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 32, 40, 32) }
            val tvLabel = TextView(this).apply { text = label; textSize = 12f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorPrimary)) }
            val tvValue = TextView(this).apply { text = value; textSize = 16f; setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurface)); setPadding(0, 8, 0, 0); setTextIsSelectable(true) }
            layout.addView(tvLabel); layout.addView(tvValue); card.addView(layout); container.addView(card)
        }

        addCard("ID", chat.optLong("id").toString())
        addCard("用户名", chat.optString("username").let { if(it.isNotEmpty()) "@$it" else "" })
        addCard("简介 / 描述", chat.optString("description").takeIf { it.isNotEmpty() } ?: chat.optString("bio"))
        
        // Telegram 新版 API 如果请求不包含成员数可能为空，安全降级
        if (chat.has("member_count")) addCard("成员总数", chat.optInt("member_count").toString())
        
        val permissions = chat.optJSONObject("permissions")
        if (permissions != null) {
            addCard("群组权限限制", permissions.toString(2).replace("\"", "").replace("{", "").replace("}", "").trim())
        }
    }

    private fun getColorAttr(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
