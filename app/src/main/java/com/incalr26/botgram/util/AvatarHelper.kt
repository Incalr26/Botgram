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

    private suspend fun getUserProfilePhotos(userId: Long): String? {
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

    private suspend fun getGroupAvatarUrl(chatId: Long): String? {
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

    /**
     * 加载头像。
     * - 若数据库有缓存 URL，直接加载，成功显示真实头像，失败保持原样（网络错误不改变）。
     * - 若无缓存，请求 API：成功则缓存并显示，无头像则显示首字符，网络错误保持原样。
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
        val chat = repo.getChatById(chatId)
        val cachedUrl = chat?.avatarUrl

        if (cachedUrl != null && cachedUrl.isNotEmpty()) {
            // 有缓存，尝试加载
            loadUrl(context, imageView, cachedUrl,
                onSuccess = { onHasAvatar?.invoke() },
                onError = {
                    // 加载失败（网络问题），不改变显示，仍触发 onNetworkError 让 UI 保持
                    Handler(Looper.getMainLooper()).post { onNetworkError?.invoke() }
                }
            )
            return
        }

        // 无缓存，请求 API
        val url: String? = when (type) {
            "private" -> if (userId != null) getUserProfilePhotos(userId) else null
            "group", "supergroup" -> getGroupAvatarUrl(chatId)
            else -> null
        }

        if (url != null) {
            // 成功获取到头像 URL，缓存并加载
            repo.updateAvatarUrl(chatId, url)
            loadUrl(context, imageView, url,
                onSuccess = { onHasAvatar?.invoke() },
                onError = {
                    Handler(Looper.getMainLooper()).post { onNetworkError?.invoke() }
                }
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
