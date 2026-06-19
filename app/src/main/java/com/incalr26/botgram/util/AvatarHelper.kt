package com.incalr26.botgram.util

import android.content.Context
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object AvatarHelper {

    // 内存缓存（进程级）
    private val userPhotoCache = ConcurrentHashMap<Long, String?>()  // userId -> url / "none"
    private val chatPhotoCache = ConcurrentHashMap<Long, String?>()
    private val pendingUserRequests = ConcurrentHashMap<Long, CompletableDeferred<String?>>()
    private val pendingChatRequests = ConcurrentHashMap<Long, CompletableDeferred<String?>>()

    private fun getToken(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取用户头像 URL，返回 null 表示网络错误，返回 "none" 表示无头像 */
    suspend fun getUserProfilePhotos(userId: Long): String? {
        // 检查内存缓存
        val cached = userPhotoCache[userId]
        if (cached != null) return cached

        val existing = pendingUserRequests[userId]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<String?>()
        pendingUserRequests[userId] = deferred
        try {
            val token = getToken() ?: run { deferred.complete(null); return null }
            val result = withContext(Dispatchers.IO) { fetchUserPhoto(token, userId) }
            userPhotoCache[userId] = result
            deferred.complete(result)
            return result
        } catch (e: Exception) {
            deferred.complete(null)
            return null
        } finally {
            pendingUserRequests.remove(userId)
        }
    }

    private suspend fun fetchUserPhoto(token: String, userId: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getUserProfilePhotos?user_id=$userId&limit=1"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) return@withContext null

                val json = JSONObject(body)
                if (!json.getBoolean("ok")) return@withContext null

                val result = json.getJSONObject("result")
                val totalCount = result.optInt("total_count", 0)
                if (totalCount == 0) return@withContext "none"

                val photosArray = result.getJSONArray("photos")
                if (photosArray.length() == 0) return@withContext "none"

                // 取第一组照片（最近的头像）
                val firstPhotoArray = photosArray.getJSONArray(0)
                if (firstPhotoArray.length() == 0) return@withContext "none"

                // 取第一个尺寸（最小尺寸，加载最快）
                val photoObj = firstPhotoArray.getJSONObject(0)
                val fileId = photoObj.getString("file_id")

                val fileUrl = getFileUrl(token, fileId)
                return@withContext fileUrl ?: null  // file 下载失败视为网络错误
            } catch (e: Exception) {
                return@withContext null
            }
        }
    }

    /** 获取群组/频道头像 URL，返回 null 表示网络错误，返回 "none" 表示无头像 */
    suspend fun getChatAvatarUrl(chatId: Long): String? {
        val cached = chatPhotoCache[chatId]
        if (cached != null) return cached

        val existing = pendingChatRequests[chatId]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<String?>()
        pendingChatRequests[chatId] = deferred
        try {
            val token = getToken() ?: run { deferred.complete(null); return null }
            val result = withContext(Dispatchers.IO) { fetchChatPhoto(token, chatId) }
            chatPhotoCache[chatId] = result
            deferred.complete(result)
            return result
        } catch (e: Exception) {
            deferred.complete(null)
            return null
        } finally {
            pendingChatRequests.remove(chatId)
        }
    }

    private suspend fun fetchChatPhoto(token: String, chatId: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getChat?chat_id=$chatId"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) return@withContext null

                val json = JSONObject(body)
                if (!json.getBoolean("ok")) return@withContext null

                val result = json.getJSONObject("result")
                val photo = result.optJSONObject("photo")
                if (photo == null) return@withContext "none"

                val fileId = photo.optString("small_file_id")
                if (fileId.isEmpty()) return@withContext null

                val fileUrl = getFileUrl(token, fileId)
                return@withContext fileUrl ?: null
            } catch (e: Exception) {
                return@withContext null
            }
        }
    }

    private suspend fun getFileUrl(token: String, fileId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) return@withContext null

                val json = JSONObject(body)
                if (json.getBoolean("ok")) {
                    val filePath = json.getJSONObject("result").getString("file_path")
                    return@withContext "https://api.telegram.org/file/bot$token/$filePath"
                } else return@withContext null
            } catch (e: Exception) {
                return@withContext null
            }
        }
    }
}
