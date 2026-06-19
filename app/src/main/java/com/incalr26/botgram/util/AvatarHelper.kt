package com.incalr26.botgram.util

import android.content.Context
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object AvatarHelper {

    private val cache = ConcurrentHashMap<Long, String?>() // userId / chatId -> avatarUrl

    private fun token(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取用户头像 URL，返回 null 表示无法获取（网络错误或无头像） */
    suspend fun getUserAvatar(userId: Long): String? {
        cache[userId]?.let { return it }
        val t = token() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$t/getUserProfilePhotos?user_id=$userId&limit=1"
                val res = ApiClient.getClient().newCall(Request.Builder().url(url).build()).execute()
                val body = res.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (!json.getBoolean("ok")) return@withContext null

                val photos = json.getJSONObject("result").optJSONArray("photos") ?: return@withContext null
                if (photos.length() == 0) return@withContext null

                val photo = photos.getJSONArray(0).getJSONObject(0)
                val fileId = photo.getString("file_id")
                val fileUrl = getFileUrl(t, fileId)
                cache[userId] = fileUrl
                fileUrl
            } catch (_: Exception) {
                null
            }
        }
    }

    /** 获取群组/频道头像 URL，返回 null 表示无法获取 */
    suspend fun getChatAvatar(chatId: Long): String? {
        cache[chatId]?.let { return it }
        val t = token() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$t/getChat?chat_id=$chatId"
                val res = ApiClient.getClient().newCall(Request.Builder().url(url).build()).execute()
                val body = res.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (!json.getBoolean("ok")) return@withContext null

                val result = json.getJSONObject("result")
                val photo = result.optJSONObject("photo") ?: return@withContext null
                val fileId = photo.optString("small_file_id", null) ?: return@withContext null
                val fileUrl = getFileUrl(t, fileId)
                cache[chatId] = fileUrl
                fileUrl
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun getFileUrl(token: String, fileId: String): String? {
        return try {
            val url = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
            val res = ApiClient.getClient().newCall(Request.Builder().url(url).build()).execute()
            val body = res.body?.string() ?: return null
            val json = JSONObject(body)
            val path = json.getJSONObject("result").getString("file_path")
            "https://api.telegram.org/file/bot$token/$path"
        } catch (_: Exception) {
            null
        }
    }
}
