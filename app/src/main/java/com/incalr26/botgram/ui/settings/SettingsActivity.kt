package com.incalr26.botgram.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import coil.Coil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.incalr26.botgram.R
import com.incalr26.botgram.ui.login.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.statusBarPlaceholder).layoutParams.height = getStatusBarHeight()

        val prefs = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)

        findViewById<MaterialSwitch>(R.id.switchUseAvatar).apply {
            isChecked = prefs.getBoolean("use_real_avatar", true)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("use_real_avatar", isChecked).apply() }
        }

        findViewById<MaterialSwitch>(R.id.switchRepeatConfirm).apply {
            isChecked = prefs.getBoolean("repeat_confirm", true)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("repeat_confirm", isChecked).apply() }
        }
        
        findViewById<MaterialSwitch>(R.id.switchAutoImage).apply {
            isChecked = prefs.getBoolean("auto_image", false)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("auto_image", isChecked).apply() }
        }
        
        findViewById<MaterialSwitch>(R.id.switchAutoSticker).apply {
            isChecked = prefs.getBoolean("auto_sticker", false)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("auto_sticker", isChecked).apply() }
        }

        // 修改保存路径
        findViewById<View>(R.id.pathConfigLayout).setOnClickListener {
            val input = EditText(this)
            input.setText(prefs.getString("media_save_path", "Botgram"))
            MaterialAlertDialogBuilder(this)
                .setTitle("媒体保存路径")
                .setMessage("默认保存至 Download/ 目录下")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val newPath = input.text.toString().trim()
                    if (newPath.isNotEmpty()) {
                        prefs.edit().putString("media_save_path", newPath).apply()
                        Toast.makeText(this, "路径已更新为: Download/$newPath", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 清除媒体缓存
        findViewById<View>(R.id.clearCacheLayout).setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                Coil.imageLoader(this@SettingsActivity).diskCache?.clear()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "媒体缓存已清空", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
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
