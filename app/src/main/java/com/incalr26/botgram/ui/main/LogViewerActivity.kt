package com.incalr26.botgram.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.incalr26.botgram.R
import com.incalr26.botgram.util.LogManager
import java.io.File
import java.io.FileOutputStream

class LogViewerActivity : AppCompatActivity() {
    private var isSystemLog = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val logContent = findViewById<TextView>(R.id.logContent)
        val dropdown = findViewById<AutoCompleteTextView>(R.id.logTypeDropdown)
        val clearButton = findViewById<MaterialButton>(R.id.clearLogButton)
        val refreshButton = findViewById<MaterialButton>(R.id.refreshLogButton)
        val exportButton = findViewById<MaterialButton>(R.id.exportLogButton)
        val sendEmailButton = findViewById<MaterialButton>(R.id.sendEmailButton)

        val logTypes = arrayOf("通讯日志 (API)", "运行日志 (Logcat)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, logTypes)
        dropdown.setAdapter(adapter)
        
        fun refresh() {
            logContent.text = if (isSystemLog) LogManager.getSystemLogContent() else LogManager.getApiLogContent(this)
        }

        dropdown.setOnItemClickListener { _, _, position, _ ->
            isSystemLog = (position == 1)
            refresh()
        }

        clearButton.setOnClickListener {
            if (isSystemLog) LogManager.clearSystemLogs() else LogManager.clearApiLogs(this)
            refresh()
        }
        refreshButton.setOnClickListener { refresh() }

        exportButton.setOnClickListener {
            try {
                val logText = logContent.text.toString()
                if (logText.isEmpty() || logText.contains("暂无")) {
                    Toast.makeText(this, "暂无日志可导出", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val exportDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Botgram/Logs")
                if (!exportDir.exists()) exportDir.mkdirs()
                val prefix = if (isSystemLog) "system_" else "api_"
                val exportFile = File(exportDir, "botgram_${prefix}export_${System.currentTimeMillis()}.log")
                FileOutputStream(exportFile).use { it.write(logText.toByteArray()) }
                Toast.makeText(this, "已导出到: ${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        sendEmailButton.setOnClickListener {
            try {
                val logText = logContent.text.toString()
                if (logText.isEmpty() || logText.contains("暂无")) {
                    Toast.makeText(this, "暂无内容可发送", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val logDir = File(getExternalFilesDir(null), "Botgram/Logs")
                if (!logDir.exists()) logDir.mkdirs()
                val prefix = if (isSystemLog) "system_" else "api_"
                val shareFile = File(logDir, "${prefix}share_temp.log")
                shareFile.writeText(logText)

                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", shareFile)
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:incalr2025@outlook.com")
                    putExtra(Intent.EXTRA_SUBJECT, "Botgram 日志反馈")
                    putExtra(Intent.EXTRA_TEXT, "请见附件日志。")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "未找到邮件客户端", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "发送准备失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
