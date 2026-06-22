package com.incalr26.botgram.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.incalr26.botgram.R
import com.incalr26.botgram.ui.login.LoginActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val statusBarPlaceholder = findViewById<View>(R.id.statusBarPlaceholder)
        statusBarPlaceholder.layoutParams.height = getStatusBarHeight()

        val prefs = getSharedPreferences("botgram_prefs", MODE_PRIVATE)

        val switchAvatar = findViewById<MaterialSwitch>(R.id.switchUseAvatar)
        switchAvatar.isChecked = prefs.getBoolean("use_real_avatar", true)
        switchAvatar.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_real_avatar", isChecked).apply()
        }

        val switchRepeat = findViewById<MaterialSwitch>(R.id.switchRepeatConfirm)
        switchRepeat.isChecked = prefs.getBoolean("repeat_confirm", true)
        switchRepeat.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("repeat_confirm", isChecked).apply()
        }
        
        val switchAutoImage = findViewById<MaterialSwitch>(R.id.switchAutoImage)
        switchAutoImage.isChecked = prefs.getBoolean("auto_image", false)
        switchAutoImage.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_image", isChecked).apply()
        }
        
        val switchAutoSticker = findViewById<MaterialSwitch>(R.id.switchAutoSticker)
        switchAutoSticker.isChecked = prefs.getBoolean("auto_sticker", false)
        switchAutoSticker.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_sticker", isChecked).apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_logout -> {
                getSharedPreferences("botgram_prefs", MODE_PRIVATE).edit().remove("bot_token").apply()
                stopService(Intent(this, com.incalr26.botgram.service.PollingService::class.java))
                com.incalr26.botgram.util.AvatarHelper.clearCache()
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }
}
