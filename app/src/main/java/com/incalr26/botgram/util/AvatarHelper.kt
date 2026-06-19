package com.incalr26.botgram.util

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object AvatarHelper {

    private val pendingRequests = mutableSetOf<Long>()
    private val mutex = Mutex()

    private fun getToken(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 通过 getChat 获取任何聊天（用户/群组/频道）的头像 URL */
    private suspend fun fetchChatAvatarUrl(chatId: Long): String? {
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
                        } else null   // 无头像
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

    /**
     * 加载头像，回调在主线程执行。
     * - onHasAvatar: 成功显示头像
     * - onNoAvatar: 确认无头像（已缓存 "none"）
     * - onNetworkError: 网络异常，不改变 UI
     */
    suspend fun loadInto(
        imageView: android.widget.ImageView,
        chatId: Long,
        onHasAvatar: (() -> Unit)? = null,
        onNoAvatar: (() -> Unit)? = null,
        onNetworkError: (() -> Unit)? = null
    ) {
        val context = imageView.context
        val repo = ChatRepository(BotApp.instance.databaseHelper)

        // 1. 读取缓存（需在 IO 线程）
        val cachedUrl = withContext(Dispatchers.IO) {
            repo.getChatById(chatId)?.avatarUrl
        }

        // 2. 已标记无头像
        if (cachedUrl == "none") {
            withContext(Dispatchers.Main) { onNoAvatar?.invoke() }
            return
        }

        // 3. 有有效缓存，直接加载
        if (cachedUrl != null && cachedUrl.isNotEmpty()) {
            loadUrl(context, imageView, cachedUrl, onHasAvatar, onNetworkError)
            return
        }

        // 4. 无缓存，发起网络请求（去重）
        mutex.withLock {
            if (pendingRequests.contains(chatId)) {
                // 已有相同请求进行中，直接返回（等待该请求完成后会刷新）
                return
            }
            pendingRequests.add(chatId)
        }

        try {
            val avatarUrl = fetchChatAvatarUrl(chatId)
            if (avatarUrl != null) {
                // 缓存头像 URL
                withContext(Dispatchers.IO) {
                    repo.updateAvatarUrl(chatId, avatarUrl)
                }
                loadUrl(context, imageView, avatarUrl, onHasAvatar, onNetworkError)
            } else {
                // 无头像，标记为 "none"
                withContext(Dispatchers.IO) {
                    repo.updateAvatarUrl(chatId, "none")
                }
                withContext(Dispatchers.Main) { onNoAvatar?.invoke() }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onNetworkError?.invoke() }
        } finally {
            mutex.withLock { pendingRequests.remove(chatId) }
        }
    }

    private fun loadUrl(
        context: Context,
        imageView: android.widget.ImageView,
        url: String,
        onSuccess: (() -> Unit)?,
        onError: (() -> Unit)?
    ) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .transformations(CircleCropTransformation())
            .target(imageView)
            .listener(
                onSuccess = { _, _ ->
                    onSuccess?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
                },
                onError = { _, _ ->
                    onError?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }
}
