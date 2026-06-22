package com.incalr26.botgram.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.incalr26.botgram.R
import com.incalr26.botgram.util.LogManager
import java.io.File
import java.io.FileOutputStream

class LogViewerActivity : AppCompatActivity() {
    private var isSystemLog = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val logContent = findViewById<TextView>(R.id.logContent)
        val clearButton = findViewById<Button>(R.id.clearLogButton)
        val refreshButton = findViewById<Button>(R.id.refreshLogButton)
        val exportButton = findViewById<Button>(R.id.exportLogButton)
        val sendEmailButton = findViewById<Button>(R.id.sendEmailButton)

        // 动态将顶部替换为可切换 Spinner，不破坏原 XML
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@LogViewerActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("通讯日志 (API)", "运行日志 (Logcat)"))
        }
        toolbar.addView(spinner)

        fun refresh() {
            logContent.text = if (isSystemLog) LogManager.getSystemLogContent() else LogManager.getApiLogContent(this)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                isSystemLog = (position == 1)
                refresh()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        clearButton.setOnClickListener {
            if (isSystemLog) LogManager.clearSystemLogs() else LogManager.clearApiLogs(this)
            refresh()
        }
        refreshButton.setOnClickListener { refresh() }

        exportButton.setOnClickListener {
            try {
                val logText = logContent.text.toString()
                if (logText.contains("暂无")) {
                    Toast.makeText(this, "暂无日志可导出", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val exportDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Botgram/logs")
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
                if (logText.contains("暂无")) {
                    Toast.makeText(this, "暂无日志可发送", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
