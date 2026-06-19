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

    // 内存缓存：只有成功获取的 URL 才存入，null 代表未请求或失败
    private val urlCache = ConcurrentHashMap<Long, String>()
    // 记录已确认无头像的 ID，避免重复请求
    private val noAvatarSet = ConcurrentHashMap.newKeySet<Long>()

    private fun token(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取用户头像 URL，返回 null 表示无头像或网络错误 */
    suspend fun getUserAvatar(userId: Long): String? {
        if (noAvatarSet.contains(userId)) return null
        urlCache[userId]?.let { return it }

        val t = token() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$t/getUserProfilePhotos?user_id=$userId&limit=1"
                val res = ApiClient.getClient().newCall(Request.Builder().url(url).build()).execute()
                val body = res.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (!json.getBoolean("ok")) return@withContext null

                val photos = json.getJSONObject("result").optJSONArray("photos")
                if (photos == null || photos.length() == 0) {
                    noAvatarSet.add(userId)
                    return@withContext null
                }

                val firstPhotoArray = photos.getJSONArray(0)
                if (firstPhotoArray.length() == 0) {
                    noAvatarSet.add(userId)
                    return@withContext null
                }

                val photoObj = firstPhotoArray.getJSONObject(0)
                val fileId = photoObj.getString("file_id")
                val fileUrl = getFileUrl(t, fileId)
                if (fileUrl != null) {
                    urlCache[userId] = fileUrl
                }
                fileUrl
            } catch (_: Exception) {
                null
            }
        }
    }

    /** 获取群组/频道头像 URL，返回 null 表示无头像或网络错误 */
    suspend fun getChatAvatar(chatId: Long): String? {
        if (noAvatarSet.contains(chatId)) return null
        urlCache[chatId]?.let { return it }

        val t = token() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$t/getChat?chat_id=$chatId"
                val res = ApiClient.getClient().newCall(Request.Builder().url(url).build()).execute()
                val body = res.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (!json.getBoolean("ok")) return@withContext null

                val result = json.getJSONObject("result")
                val photo = result.optJSONObject("photo")
                if (photo == null) {
                    noAvatarSet.add(chatId)
                    return@withContext null
                }

                val fileId = photo.optString("small_file_id", null) ?: run {
                    noAvatarSet.add(chatId)
                    return@withContext null
                }
                val fileUrl = getFileUrl(t, fileId)
                if (fileUrl != null) {
                    urlCache[chatId] = fileUrl
                }
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

    /** 切换 Bot 时清空缓存 */
    fun clearCache() {
        urlCache.clear()
        noAvatarSet.clear()
    }
}
