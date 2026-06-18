package com.incalr26.botgram.util

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
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

    fun getChatAvatarUrl(chatId: Long): String {
        // 群组头像可以使用公开链接，私聊也可用（但无头像则默认图）
        return "https://t.me/i/userpic/320/$chatId.jpg"
    }

    /** 加载头像到 ImageView，Coil 自动处理 placeholder/error */
    suspend fun loadInto(imageView: android.widget.ImageView, userId: Long?, chatId: Long, type: String) {
        val url: String? = when {
            type == "private" && userId != null -> getUserProfilePhotos(userId)
            type == "group" || type == "supergroup" -> getChatAvatarUrl(chatId)
            else -> null
        }
        val request = ImageRequest.Builder(imageView.context)
            .data(url ?: getChatAvatarUrl(chatId)) // fallback 到公开 URL
            .crossfade(true)
            .error(android.R.drawable.ic_menu_report_image)
            .placeholder(android.R.drawable.ic_menu_report_image)
            .target(imageView)
            .build()
        imageView.context.imageLoader.enqueue(request)
    }
}
