package com.incalr26.botgram.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.incalr26.botgram.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object FileHelper {

    suspend fun getTelegramFileUrl(fileId: String, botToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
            val request = Request.Builder().url(url).build()
            val response = ApiClient.getClient().newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                if (json.getBoolean("ok")) {
                    val filePath = json.getJSONObject("result").getString("file_path")
                    return@withContext "https://api.telegram.org/file/bot$botToken/$filePath"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun saveMediaToStorage(context: Context, url: String, subDir: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = ApiClient.getClient().newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val prefs = context.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
                val basePath = prefs.getString("media_save_path", "Download/Botgram") ?: "Download/Botgram"
                
                var targetDir = File(Environment.getExternalStorageDirectory(), "$basePath/$subDir")
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Botgram/$subDir")
                    targetDir.mkdirs()
                }
                
                val destFile = File(targetDir, fileName)
                
                val inputStream = response.body!!.byteStream()
                val outputStream = FileOutputStream(destFile)
                inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()
                
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null) { _, _ -> }
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }
}
