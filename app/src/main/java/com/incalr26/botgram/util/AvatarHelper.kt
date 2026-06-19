package com.incalr26.botgram.util

import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object AvatarHelper {

    private fun getToken(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", android.content.Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取用户头像 URL。返回 null 表示网络错误，返回 "none" 表示无头像，否则为 URL */
    suspend fun getUserProfilePhotos(userId: Long): String? {
        val repo = ChatRepository(BotApp.instance.databaseHelper)
        // 先查缓存（包括 "none" 也读出来，但下面会重新尝试请求）
        val cached = withContext(Dispatchers.IO) { repo.getChatById(userId)?.avatarUrl }
        if (!cached.isNullOrEmpty() && cached != "none") {
            return cached
        }

        val token = getToken() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getUserProfilePhotos?user_id=$userId&limit=1"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getBoolean("ok")) {
                        val photos = json.getJSONObject("result").getJSONArray("photos")
                        if (photos.length() > 0) {
                            val firstPhoto = photos.getJSONArray(0)
                            if (firstPhoto.length() > 0) {
                                val fileId = firstPhoto.getJSONObject(0).getString("file_id")
                                val fileUrl = getFileUrl(token, fileId)
                                if (fileUrl != null) {
                                    repo.updateAvatarUrl(userId, fileUrl)
                                    return@withContext fileUrl
                                }
                            }
                        }
                        // 无头像，写入 "none"
                        repo.updateAvatarUrl(userId, "none")
                        return@withContext "none"
                    }
                }
            } catch (_: Exception) {}
            null // 网络错误
        }
    }

    /** 获取群组/频道头像 URL。返回 null 表示网络错误，返回 "none" 表示无头像，否则为 URL */
    suspend fun getChatAvatarUrl(chatId: Long): String? {
        val repo = ChatRepository(BotApp.instance.databaseHelper)
        val cached = withContext(Dispatchers.IO) { repo.getChatById(chatId)?.avatarUrl }
        if (!cached.isNullOrEmpty() && cached != "none") {
            return cached
        }

        val token = getToken() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getChat?chat_id=$chatId"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getBoolean("ok")) {
                        val chatObj = json.getJSONObject("result")
                        val photo = chatObj.optJSONObject("photo")
                        if (photo != null) {
                            val fileId = photo.getString("small_file_id")
                            val fileUrl = getFileUrl(token, fileId)
                            if (fileUrl != null) {
                                repo.updateAvatarUrl(chatId, fileUrl)
                                return@withContext fileUrl
                            }
                        }
                        repo.updateAvatarUrl(chatId, "none")
                        return@withContext "none"
                    }
                }
            } catch (_: Exception) {}
            null
        }
    }

    private suspend fun getFileUrl(token: String, fileId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getBoolean("ok")) {
                        val filePath = json.getJSONObject("result").getString("file_path")
                        "https://api.telegram.org/file/bot$token/$filePath"
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
