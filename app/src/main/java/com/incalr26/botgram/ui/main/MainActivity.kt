package com.incalr26.botgram.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import com.google.android.material.navigation.NavigationView
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.BuildConfig
import com.incalr26.botgram.R
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.ui.login.LoginActivity
import com.incalr26.botgram.util.AvatarHelper
import com.incalr26.botgram.util.NetworkStateHolder
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var networkBar: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var contentLayout: View
    private var downX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        contentLayout = findViewById(R.id.contentLayout)

        navigationView.menu.findItem(R.id.nav_version)?.title = "版本 ${BuildConfig.VERSION_NAME}"

        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                drawerLayout.closeDrawer(Gravity.LEFT)
            } else {
                drawerLayout.openDrawer(Gravity.LEFT)
            }
        }

        contentLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                        val dx = event.x - downX
                        if (dx > 80) {
                            drawerLayout.openDrawer(Gravity.LEFT)
                            return@setOnTouchListener true
                        }
                    }
                    false
                }
                else -> false
            }
        }

        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.app_name, R.string.app_name)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        networkBar = findViewById(R.id.networkStatusBar)
        updateNetworkStatus()

        // 恢复旧数据
        recoverLegacyChats()

        loadBotInfo()

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_add_chat -> {
                    AddChatDialogFragment().show(supportFragmentManager, "AddChat")
                }
                R.id.nav_view_log -> {
                    startActivity(Intent(this, LogViewerActivity::class.java))
                }
                R.id.nav_logout -> {
                    getSharedPreferences("botgram_prefs", MODE_PRIVATE)
                        .edit().remove("bot_token").apply()
                    stopService(Intent(this, com.incalr26.botgram.service.PollingService::class.java))
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        // 监听网络状态 LiveData
        NetworkStateHolder.isConnected.observe(this, Observer { connected ->
            networkBar.visibility = if (connected) View.GONE else View.VISIBLE
        })
    }

    private fun recoverLegacyChats() {
        val db = BotApp.instance.databaseHelper.writableDatabase
        val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
            .getString("bot_token", "") ?: ""
        val newHash = token.hashCode().toString()
        db.execSQL("UPDATE ${com.incalr26.botgram.data.local.DatabaseHelper.TABLE_CHATS} SET ${com.incalr26.botgram.data.local.DatabaseHelper.COL_ACCOUNT_HASH} = ? WHERE ${com.incalr26.botgram.data.local.DatabaseHelper.COL_ACCOUNT_HASH} = 'legacy'", arrayOf(newHash))
    }

    private fun loadBotInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
                    .getString("bot_token", "") ?: return@launch
                val getMeUrl = "https://api.telegram.org/bot$token/getMe"
                val meRequest = Request.Builder().url(getMeUrl).build()
                val meResponse = ApiClient.getClient().newCall(meRequest).execute()
                if (!meResponse.isSuccessful) return@launch
                val meJson = JSONObject(meResponse.body?.string() ?: "")
                if (!meJson.getBoolean("ok")) return@launch
                val bot = meJson.getJSONObject("result")
                val botId = bot.getLong("id")
                val firstName = bot.optString("first_name", "Bot")
                val username = bot.optString("username", null)

                var description: String? = null
                try {
                    val descUrl = "https://api.telegram.org/bot$token/getMyDescription"
                    val descRequest = Request.Builder().url(descUrl).build()
                    val descResponse = ApiClient.getClient().newCall(descRequest).execute()
                    if (descResponse.isSuccessful) {
                        val descJson = JSONObject(descResponse.body?.string() ?: "")
                        if (descJson.getBoolean("ok")) {
                            description = descJson.getJSONObject("result").optString("description", null)
                        }
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    val headerView = navigationView.getHeaderView(0)
                    headerView.findViewById<TextView>(R.id.botName).text = firstName
                    headerView.findViewById<TextView>(R.id.botUsername).text =
                        if (username != null) "@$username" else "无用户名"
                    headerView.findViewById<TextView>(R.id.botDescription).text = description ?: ""

                    val avatarView = headerView.findViewById<ImageView>(R.id.botAvatar)
                    val fallbackView = headerView.findViewById<TextView>(R.id.botAvatarFallback)
                    val fallback = firstName.take(1).uppercase()
                    fallbackView.text = fallback
                    fallbackView.visibility = View.VISIBLE
                    avatarView.visibility = View.GONE

                    try {
                        AvatarHelper.loadInto(
                            avatarView, botId, botId, "private",
                            onSuccess = {
                                fallbackView.visibility = View.GONE
                                avatarView.visibility = View.VISIBLE
                            },
                            onError = {
                                fallbackView.visibility = View.VISIBLE
                                avatarView.visibility = View.GONE
                            }
                        )
                    } catch (e: Exception) {
                        fallbackView.visibility = View.VISIBLE
                        avatarView.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
            super.onBackPressed()
        }
    }

    private fun updateNetworkStatus() {
        // 初始状态由 LiveData 决定，无需 BroadcastReceiver
    }
}
