package com.incalr26.botgram.ui.settings

import android.os.Bundle
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import com.incalr26.botgram.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val switchAvatar = findViewById<Switch>(R.id.switchUseAvatar)
        val prefs = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
        switchAvatar.isChecked = prefs.getBoolean("use_real_avatar", true)

        switchAvatar.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_real_avatar", isChecked).apply()
        }
    }
}
