package com.incalr26.botgram.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object AvatarHelper {

    private fun getToken(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取用户头像 URL（通过 getUserProfilePhotos） */
    suspend fun getUserAvatarUrl(userId: Long): String? {
        val token = getToken() ?: return null
        val url = "https://api.telegram.org/bot$token/getUserProfilePhotos?user_id=$userId&limit=1"
        return withContext(Dispatchers.IO) {
            try {
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
                                getFileUrl(token, fileId)
                            } else null
                        } else null
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 获取群组/频道头像 URL（通过 getChat） */
    suspend fun getChatAvatarUrl(chatId: Long): String? {
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
        val url = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
        return withContext(Dispatchers.IO) {
            try {
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
     * 加载头像，回调保证在主线程执行。
     * - onHasAvatar: 成功获取到头像并显示
     * - onNoAvatar: 确认无头像（API 返回空）
     * - onNetworkError: 网络异常，保持原样
     */
    suspend fun loadInto(
        imageView: android.widget.ImageView,
        userId: Long?,
        chatId: Long,
        type: String,
        onHasAvatar: (() -> Unit)? = null,
        onNoAvatar: (() -> Unit)? = null,
        onNetworkError: (() -> Unit)? = null
    ) {
        val context = imageView.context
        val repo = ChatRepository(BotApp.instance.databaseHelper)

        // 1. 先从数据库缓存获取
        val chat = repo.getChatById(chatId)
        val cachedUrl = chat?.avatarUrl

        if (cachedUrl == "none") {
            Handler(Looper.getMainLooper()).post { onNoAvatar?.invoke() }
            return
        } else if (cachedUrl != null && cachedUrl.isNotEmpty()) {
            loadUrl(context, imageView, cachedUrl,
                onSuccess = { onHasAvatar?.invoke() },
                onError = { onNetworkError?.invoke() }
            )
            return
        }

        // 2. 没有缓存，请求 API
        val url: String? = when (type) {
            "private" -> if (userId != null) getUserAvatarUrl(userId) else null
            "group", "supergroup" -> getChatAvatarUrl(chatId)
            else -> null
        }

        if (url != null) {
            // 成功获取到头像 URL，缓存并加载
            repo.updateAvatarUrl(chatId, url)
            loadUrl(context, imageView, url,
                onSuccess = { onHasAvatar?.invoke() },
                onError = { onNetworkError?.invoke() }
            )
        } else {
            // 无头像
            Handler(Looper.getMainLooper()).post { onNoAvatar?.invoke() }
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
                    Handler(Looper.getMainLooper()).post { onSuccess?.invoke() }
                },
                onError = { _, _ ->
                    Handler(Looper.getMainLooper()).post { onError?.invoke() }
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }
}
