package com.incalr26.botgram.util

import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object AvatarHelper {

    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<String?>>()
    private val mutex = Mutex()

    private fun getToken(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", android.content.Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取头像 URL，可能返回 null（无头像） */
    suspend fun getAvatarUrl(chatId: Long): String? {
        // 1. 先查数据库缓存
        val repo = ChatRepository(BotApp.instance.databaseHelper)
        val cached = withContext(Dispatchers.IO) { repo.getChatById(chatId)?.avatarUrl }
        if (cached == "none") return null
        if (!cached.isNullOrEmpty()) return cached

        // 2. 检查是否有正在进行的请求
        val existing = pendingRequests[chatId]
        if (existing != null) {
            return existing.await() // 挂起等待结果
        }

        // 3. 创建新请求
        val deferred = CompletableDeferred<String?>()
        pendingRequests[chatId] = deferred

        try {
            val url = withContext(Dispatchers.IO) {
                fetchAvatarUrl(chatId)
            }
            // 保存结果
            withContext(Dispatchers.IO) {
                repo.updateAvatarUrl(chatId, url ?: "none")
            }
            deferred.complete(url)
            return url
        } catch (e: Exception) {
            deferred.complete(null)
            return null
        } finally {
            pendingRequests.remove(chatId)
        }
    }

    private suspend fun fetchAvatarUrl(chatId: Long): String? {
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
                            getFileUrl(token, fileId)
                        } else null
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
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
