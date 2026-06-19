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
import androidx.appcompat.app.AlertDialog
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
import com.incalr26.botgram.ui.settings.SettingsActivity
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

        val statusBarPlaceholder = findViewById<View>(R.id.statusBarPlaceholder)
        statusBarPlaceholder.layoutParams.height = getStatusBarHeight()

        val headerView = navigationView.getHeaderView(0)
        val headerStatusBarSpace = headerView.findViewById<View>(R.id.statusBarSpace)
        headerStatusBarSpace.post { headerStatusBarSpace.layoutParams.height = getStatusBarHeight() }

        navigationView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(navigationView) { v, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = navBarHeight)
            insets
        }

        val versionItem = navigationView.menu.findItem(R.id.nav_version)
        val sp = SpannableString("版本 ${BuildConfig.VERSION_NAME}").apply {
            setSpan(ForegroundColorSpan(Color.GRAY), 0, length, 0)
            setSpan(AbsoluteSizeSpan(12, true), 0, length, 0)
        }
        versionItem?.title = sp

        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.LEFT)) drawerLayout.closeDrawer(Gravity.LEFT)
            else drawerLayout.openDrawer(Gravity.LEFT)
        }

        contentLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; false }
                MotionEvent.ACTION_MOVE -> {
                    if (!drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                        if (event.x - downX > 80) {
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
                R.id.nav_add_chat -> AddChatDialogFragment().show(supportFragmentManager, "AddChat")
                R.id.nav_view_log -> startActivity(Intent(this, LogViewerActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_about -> showAboutDialog()
                R.id.nav_logout -> {
                    getSharedPreferences("botgram_prefs", MODE_PRIVATE).edit().remove("bot_token").apply()
                    stopService(Intent(this, com.incalr26.botgram.service.PollingService::class.java))
                    AvatarHelper.clearCache()
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

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("关于 Botgram")
            .setMessage("版本: ${BuildConfig.VERSION_NAME}\n\n" +
                    "Telegram 频道: @Botgram_Channel\n" +
                    "Telegram 群组: @Botgram_ChatGroup\n" +
                    "GitHub: https://github.com/Incalr26/Botgram")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    private fun recoverLegacyChats() { /* 不变，省略 */ }

    private fun loadBotInfo() { /* 不变，省略 */ }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) drawerLayout.closeDrawer(navigationView)
        else super.onBackPressed()
    }

    private fun updateNetworkStatus() {}
}
