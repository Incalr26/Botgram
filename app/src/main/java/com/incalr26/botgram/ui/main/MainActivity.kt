package com.incalr26.botgram.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        contentLayout = findViewById(R.id.contentLayout)

        // 主内容状态栏占位
        val statusBarPlaceholder = findViewById<View>(R.id.statusBarPlaceholder)
        statusBarPlaceholder.layoutParams.height = getStatusBarHeight()

        // 菜单头部状态栏占位（post 确保布局完成）
        val headerView = navigationView.getHeaderView(0)
        val headerStatusBarSpace = headerView.findViewById<View>(R.id.statusBarSpace)
        headerStatusBarSpace.post {
            headerStatusBarSpace.layoutParams.height = getStatusBarHeight()
        }

        // 菜单底部留出导航栏高度
        navigationView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(navigationView) { v, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = navBarHeight)
            insets
        }

        // 版本号灰色小字
        val versionItem = navigationView.menu.findItem(R.id.nav_version)
        val versionText = "版本 ${BuildConfig.VERSION_NAME}"
        val sp = SpannableString(versionText).apply {
            setSpan(ForegroundColorSpan(Color.GRAY), 0, length, 0)
            setSpan(AbsoluteSizeSpan(12, true), 0, length, 0)
        }
        versionItem?.title = sp

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

        NetworkStateHolder.isConnected.observe(this, Observer { connected ->
            networkBar.visibility = if (connected) View.GONE else View.VISIBLE
        })
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
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

                    // 使用 loadInto 加载 Bot 头像
                    AvatarHelper.loadInto(
                        avatarView, botId, botId, "private",
                        onHasAvatar = {
                            fallbackView.visibility = View.GONE
                            avatarView.visibility = View.VISIBLE
                        },
                        onNoAvatar = {
                            fallbackView.visibility = View.VISIBLE
                            avatarView.visibility = View.GONE
                        },
                        onNetworkError = {
                            // 保持原样
                        }
                    )
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

    private fun updateNetworkStatus() {}
}
