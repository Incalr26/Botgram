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

    private const val NO_PHOTO = "__NO_PHOTO__"

    private fun getToken(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取用户头像 URL，返回三态：URL / NO_PHOTO / null(网络错误) */
    suspend fun getUserProfilePhotos(userId: Long): String? {
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
                                return@withContext getFileUrl(token, fileId) ?: null
                            }
                        }
                        return@withContext NO_PHOTO
                    }
                }
            } catch (_: Exception) {}
            null
        }
    }

    /** 获取群组/频道头像 URL */
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
                            return@withContext getFileUrl(token, fileId) ?: null
                        }
                        return@withContext NO_PHOTO
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

    /**
     * 加载头像并回调。
     * - onHasAvatar: 成功显示图片
     * - onNoAvatar: 确认无头像
     * - onNetworkError: 网络错误，保持原样
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

        // 在 IO 线程查询缓存
        val chat = withContext(Dispatchers.IO) { repo.getChatById(chatId) }
        val cachedUrl = chat?.avatarUrl

        if (cachedUrl == "none") {
            Handler(Looper.getMainLooper()).post { onNoAvatar?.invoke() }
            return
        } else if (cachedUrl != null && cachedUrl.isNotEmpty()) {
            loadUrl(context, imageView, cachedUrl, onHasAvatar, onNetworkError)
            return
        }

        // 请求 API
        val result = when (type) {
            "private" -> {
                val idToUse = userId ?: chatId  // 私聊中 chatId 就是用户 ID
                getUserProfilePhotos(idToUse)
            }
            "group", "supergroup", "channel" -> getChatAvatarUrl(chatId)
            else -> null
        }

        when {
            result == null -> {
                // 网络错误
                Handler(Looper.getMainLooper()).post { onNetworkError?.invoke() }
            }
            result == NO_PHOTO -> {
                // 确认无头像，缓存
                withContext(Dispatchers.IO) { repo.updateAvatarUrl(chatId, "none") }
                Handler(Looper.getMainLooper()).post { onNoAvatar?.invoke() }
            }
            else -> {
                // 成功获取 URL
                withContext(Dispatchers.IO) { repo.updateAvatarUrl(chatId, result) }
                loadUrl(context, imageView, result, onHasAvatar, onNetworkError)
            }
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
                onSuccess = { _, _ -> Handler(Looper.getMainLooper()).post { onSuccess?.invoke() } },
                onError = { _, _ -> Handler(Looper.getMainLooper()).post { onError?.invoke() } }
            )
            .build()
        context.imageLoader.enqueue(request)
    }
}
