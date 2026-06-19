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

    suspend fun getUserProfilePhotos(userId: Long): String? {
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

    /** 加载头像，仅回调有/无头像，网络错误时不改变 UI */
    suspend fun loadInto(
        imageView: android.widget.ImageView,
        userId: Long?,
        chatId: Long,
        type: String,
        onHasAvatar: (() -> Unit)? = null,
        onNoAvatar: (() -> Unit)? = null
    ) {
        val context = imageView.context
        val repo = ChatRepository(BotApp.instance.databaseHelper)

        val chat = repo.getChatById(chatId)
        val cachedUrl = chat?.avatarUrl

        if (cachedUrl != null && cachedUrl.isNotEmpty()) {
            loadUrl(context, imageView, cachedUrl, onHasAvatar, onNoAvatar)
            return
        }

        val url = when (type) {
            "private" -> if (userId != null) getUserProfilePhotos(userId) else null
            "group", "supergroup" -> getChatAvatarUrl(chatId)
            else -> null
        }

        if (url != null) {
            repo.updateAvatarUrl(chatId, url)
            loadUrl(context, imageView, url, onHasAvatar, onNoAvatar)
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
                    // 网络错误时回调 onError，但不再改变 UI（即保持当前首字符）
                    Handler(Looper.getMainLooper()).post { onError?.invoke() }
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }
}
