package com.incalr26.botgram.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.incalr26.botgram.R
import com.incalr26.botgram.util.LogManager
import java.io.File
import java.io.FileOutputStream

class LogViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val logContent = findViewById<TextView>(R.id.logContent)
        val clearButton = findViewById<Button>(R.id.clearLogButton)
        val refreshButton = findViewById<Button>(R.id.refreshLogButton)
        val exportButton = findViewById<Button>(R.id.exportLogButton)
        val sendEmailButton = findViewById<Button>(R.id.sendEmailButton)

        fun refresh() {
            logContent.text = LogManager.getLogContent(this)
        }
        refresh()

        clearButton.setOnClickListener {
            LogManager.clearLogs(this)
            refresh()
        }
        refreshButton.setOnClickListener { refresh() }

        // 导出日志：复制到下载目录并提示
        exportButton.setOnClickListener {
            try {
                val logText = LogManager.getLogContent(this)
                if (logText == "暂无日志") {
                    Toast.makeText(this, "暂无日志可导出", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val exportDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Botgram/logs")
                if (!exportDir.exists()) exportDir.mkdirs()
                val exportFile = File(exportDir, "botgram_export_${System.currentTimeMillis()}.log")
                FileOutputStream(exportFile).use { it.write(logText.toByteArray()) }
                Toast.makeText(this, "已导出到: ${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // 发送到邮箱：通过 FileProvider 分享
        sendEmailButton.setOnClickListener {
            try {
                val logText = LogManager.getLogContent(this)
                if (logText == "暂无日志") {
                    Toast.makeText(this, "暂无日志可发送", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // 将日志写入临时文件
                val logDir = File(getExternalFilesDir(null), "Botgram/Logs")
                if (!logDir.exists()) logDir.mkdirs()
                val shareFile = File(logDir, "share_temp.log")
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
}
