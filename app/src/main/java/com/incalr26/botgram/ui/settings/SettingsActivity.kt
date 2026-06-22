package com.incalr26.botgram.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import coil.Coil
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

        val pathInput = findViewById<TextInputEditText>(R.id.pathEditText)
        val pathLayout = findViewById<TextInputLayout>(R.id.pathInputLayout)

        pathInput.setText(prefs.getString("media_save_path", "Botgram"))
        
        pathLayout.setEndIconOnClickListener {
            val newPath = pathInput.text.toString().trim()
            if (newPath.isNotEmpty()) {
                prefs.edit().putString("media_save_path", newPath).apply()
                Toast.makeText(this, "下载路径已保存", Toast.LENGTH_SHORT).show()
                pathInput.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(pathInput.windowToken, 0)
            } else {
                Toast.makeText(this, "路径不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.clearCacheLayout).setOnClickListener {
            prefs.edit().remove("unlocked_media").apply()
            CoroutineScope(Dispatchers.IO).launch {
                Coil.imageLoader(this@SettingsActivity).diskCache?.clear()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "媒体缓存与记录已清空", Toast.LENGTH_SHORT).show()
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
