package com.incalr26.botgram.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.incalr26.botgram.R
import com.incalr26.botgram.util.LogManager

class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val errorText = findViewById<TextView>(R.id.errorText)
        val restartButton = findViewById<Button>(R.id.restartButton)

        val stackTrace = intent.getStringExtra("stack_trace") ?: "未知错误"
        errorText.text = "应用崩溃，请将以下信息反馈给开发者：\n\n$stackTrace"

        // 将崩溃信息写入日志
        LogManager.write(this, "CRASH: $stackTrace")

        restartButton.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            finish()
        }
    }
}
