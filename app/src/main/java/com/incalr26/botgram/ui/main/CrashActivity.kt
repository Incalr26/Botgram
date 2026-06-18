package com.incalr26.botgram.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.incalr26.botgram.R
import com.incalr26.botgram.util.LogManager

class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val errorText = findViewById<TextView>(R.id.errorText)
        val copyButton = findViewById<Button>(R.id.copyButton)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val restartButton = findViewById<Button>(R.id.restartButton)

        val stackTrace = intent.getStringExtra("stack_trace") ?: "未知错误"
        errorText.text = "应用崩溃，请将以下信息反馈给开发者：\n\n$stackTrace"

        LogManager.write(this, "CRASH: $stackTrace")

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("crash_log", stackTrace)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        sendButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, stackTrace)
                putExtra(Intent.EXTRA_SUBJECT, "Botgram 崩溃报告")
            }
            startActivity(Intent.createChooser(intent, "发送反馈"))
        }

        restartButton.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
            finish()
        }
    }
}
