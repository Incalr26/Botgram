package com.incalr26.botgram.util

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object LogManager {
    private const val LOG_DIR = "Botgram/Logs"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private fun getLogFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return File(dir, "botgram_api_$dateStr.log")
    }

    fun write(context: Context, message: String) {
        val file = getLogFile(context)
        val timestamp = timeFormat.format(Date())
        file.appendText("$timestamp $message\n")
        if (file.length() > MAX_LOG_SIZE) {
            val trimmed = file.readLines().takeLast(100).joinToString("\n")
            file.writeText(trimmed)
        }
    }

    fun getApiLogContent(context: Context): String {
        val file = getLogFile(context)
        return if (file.exists()) file.readText() else "暂无通讯日志"
    }
    
    fun getSystemLogContent(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -t 500 -v time")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val log = StringBuilder()
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                log.append(line).append("\n")
            }
            if (log.isEmpty()) "暂无系统运行日志" else log.toString()
        } catch (e: Exception) {
            "无法获取系统日志: ${e.message}"
        }
    }

    fun clearApiLogs(context: Context) {
        val dir = File(context.getExternalFilesDir(null), LOG_DIR)
        dir.listFiles()?.forEach { it.delete() }
    }
    
    fun clearSystemLogs() {
        try {
            Runtime.getRuntime().exec("logcat -c")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
