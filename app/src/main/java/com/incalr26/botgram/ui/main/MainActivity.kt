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
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.ui.login.LoginActivity
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var networkBar: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var contentLayout: View
    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateNetworkStatus()
        }
    }
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

        // 点击导航按钮直接打开抽屉
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                drawerLayout.closeDrawer(Gravity.LEFT)
            } else {
                drawerLayout.openDrawer(Gravity.LEFT)
            }
        }

        // 自定义全局右滑检测
        contentLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                        val dx = event.x - downX
                        if (dx > 80) { // 右滑超过阈值
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
    }

    private fun loadBotInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
                    .getString("bot_token", "") ?: return@launch
                val url = "https://api.telegram.org/bot$token/getMe"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getBoolean("ok")) {
                        val bot = json.getJSONObject("result")
                        val firstName = bot.optString("first_name", "Bot")
                        val username = bot.optString("username", null)
                        withContext(Dispatchers.Main) {
                            val headerView = navigationView.getHeaderView(0)
                            headerView.findViewById<TextView>(R.id.botName).text = firstName
                            headerView.findViewById<TextView>(R.id.botUsername).text =
                                if (username != null) "@$username" else "无用户名"
                            headerView.findViewById<TextView>(R.id.botAvatar).text =
                                firstName.take(1).uppercase()
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(networkReceiver)
    }

    private fun updateNetworkStatus() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        val connected = activeNetwork != null && activeNetwork.isConnected
        networkBar.visibility = if (connected) View.GONE else View.VISIBLE
    }
}
