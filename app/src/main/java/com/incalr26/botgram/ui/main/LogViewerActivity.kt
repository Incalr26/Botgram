package com.incalr26.botgram.ui.main

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.incalr26.botgram.R
import com.incalr26.botgram.util.LogManager

class LogViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val logContent = findViewById<TextView>(R.id.logContent)
        val clearButton = findViewById<Button>(R.id.clearLogButton)
        val refreshButton = findViewById<Button>(R.id.refreshLogButton)

        fun refresh() {
            logContent.text = LogManager.getLogContent(this)
        }
        refresh()

        clearButton.setOnClickListener {
            LogManager.clearLogs(this)
            refresh()
        }
        refreshButton.setOnClickListener { refresh() }
    }
}
