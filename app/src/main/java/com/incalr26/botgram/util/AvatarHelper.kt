package com.incalr26.botgram.util

import android.content.Context
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
        val repo = ChatRepository(BotApp.instance.databaseHelper)
        val chat = repo.getChatById(chatId)
        if (chat?.avatarUrl != null) return chat.avatarUrl

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
                                // 返回文件 URL
                                fileUrl
                            } else null
                        } else null
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun loadInto(
        imageView: android.widget.ImageView,
        userId: Long?,
        chatId: Long,
        type: String
    ) {
        val context = imageView.context
        val url: String? = when (type) {
            "private" -> if (userId != null) getUserProfilePhotos(userId) else null
            "group", "supergroup" -> getGroupAvatarUrl(chatId)
            else -> null
        }

        val request = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .transformations(CircleCropTransformation())
            .placeholder(android.R.drawable.ic_menu_report_image)
            .error(android.R.drawable.ic_menu_report_image)
            .target(imageView)
            .build()
        context.imageLoader.enqueue(request)
    }
}
