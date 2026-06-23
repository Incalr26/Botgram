package com.incalr26.botgram.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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

    // 将系统选取的 Uri 复制到私有缓存区供上传读取
    suspend fun uriToTempFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            var fileName = "upload_file_${System.currentTimeMillis()}"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                }
            }
            val tempFile = File(context.cacheDir, fileName)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()
            return@withContext tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
