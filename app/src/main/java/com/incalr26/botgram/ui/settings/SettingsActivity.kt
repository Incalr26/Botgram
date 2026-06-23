package com.incalr26.botgram.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import coil.Coil
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.incalr26.botgram.R
import com.incalr26.botgram.ui.login.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        if (size < 1024) return "${size} B"
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024f)
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024f * 1024f))
        return String.format("%.2f GB", size / (1024f * 1024f * 1024f))
    }

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

        val pathInput = findViewById<TextInputEditText>(R.id.pathEditText)
        val saveBtn = findViewById<MaterialButton>(R.id.btnSavePath)
        
        var currentPath = prefs.getString("media_save_path", "Download/Botgram") ?: "Download/Botgram"
        pathInput.setText(currentPath)
        saveBtn.isEnabled = false // 禁用时由 Material 3 自动变灰

        pathInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                saveBtn.isEnabled = s?.toString()?.trim() != currentPath
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        saveBtn.setOnClickListener {
            val newPath = pathInput.text.toString().trim()
            if (newPath.isNotEmpty()) {
                currentPath = newPath
                prefs.edit().putString("media_save_path", newPath).apply()
                saveBtn.isEnabled = false
                Toast.makeText(this, "保存路径已更新", Toast.LENGTH_SHORT).show()
                pathInput.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(pathInput.windowToken, 0)
            } else {
                Toast.makeText(this, "路径不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        val clearSubtitle = findViewById<TextView>(R.id.clearCacheSubtitle)
        
        // 异步计算缓存大小
        CoroutineScope(Dispatchers.IO).launch {
            val cacheSize = Coil.imageLoader(this@SettingsActivity).diskCache?.size ?: 0L
            withContext(Dispatchers.Main) {
                clearSubtitle.text = "当前占用: ${formatSize(cacheSize)}"
            }
        }

        findViewById<View>(R.id.clearCacheLayout).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清除缓存")
                .setMessage("这将会清理本地已下载的图片和贴纸缓存文件，确认要继续吗？")
                .setPositiveButton("清除") { _, _ ->
                    prefs.edit().remove("unlocked_media").apply()
                    CoroutineScope(Dispatchers.IO).launch {
                        Coil.imageLoader(this@SettingsActivity).diskCache?.clear()
                        withContext(Dispatchers.Main) {
                            clearSubtitle.text = "当前占用: 0 B"
                            Toast.makeText(this@SettingsActivity, "媒体缓存与记录已清空", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
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
