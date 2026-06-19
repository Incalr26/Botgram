package com.incalr26.botgram.util

import android.util.Log
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

object AvatarHelper {

    private fun getToken(): String? {
        return BotApp.instance.getSharedPreferences("botgram_prefs", android.content.Context.MODE_PRIVATE)
            .getString("bot_token", null)
    }

    /** 获取用户头像 URL。返回 null 表示网络错误或解析失败，返回 "none" 表示确认无头像，否则为图片 URL */
    suspend fun getUserProfilePhotos(userId: Long): String? {
        val token = getToken()
        if (token == null) {
            Log.e("AvatarHelper", "Token is null")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getUserProfilePhotos?user_id=$userId&limit=1"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Log.e("AvatarHelper", "getUserProfilePhotos failed: ${response.code}")
                    return@withContext null
                }

                val json = try {
                    JSONObject(body)
                } catch (e: JSONException) {
                    Log.e("AvatarHelper", "JSON parse error: $body", e)
                    return@withContext null
                }

                if (!json.getBoolean("ok")) {
                    Log.e("AvatarHelper", "API not ok: $body")
                    return@withContext null
                }

                val result = json.getJSONObject("result")
                // 检查 total_count
                val totalCount = result.optInt("total_count", 0)
                if (totalCount == 0) {
                    Log.i("AvatarHelper", "User $userId has no profile photos")
                    return@withContext "none"
                }

                val photosArray = result.getJSONArray("photos")
                if (photosArray.length() == 0) {
                    return@withContext "none"
                }

                // photos 是数组的数组，取第一个元素（最近的照片），再取最大尺寸（最后一张）
                val firstPhotoArray = photosArray.getJSONArray(0)
                if (firstPhotoArray.length() == 0) {
                    return@withContext "none"
                }

                // 获取最大尺寸（通常在数组末尾，但为了安全我们取最后一项）
                val photoObj = firstPhotoArray.getJSONObject(firstPhotoArray.length() - 1)
                val fileId = photoObj.getString("file_id")

                // 获取真实下载链接
                val fileUrl = getFileUrl(token, fileId)
                if (fileUrl == null) {
                    Log.e("AvatarHelper", "Failed to get file URL for file_id: $fileId")
                    return@withContext null
                }

                Log.i("AvatarHelper", "Got avatar URL for user $userId: $fileUrl")
                return@withContext fileUrl
            } catch (e: JSONException) {
                Log.e("AvatarHelper", "JSON exception for user $userId", e)
                return@withContext null
            } catch (e: Exception) {
                Log.e("AvatarHelper", "Exception for user $userId", e)
                return@withContext null
            }
        }
    }

    /** 获取群组/频道头像 URL。返回 null 表示错误，返回 "none" 表示无头像，否则为图片 URL */
    suspend fun getChatAvatarUrl(chatId: Long): String? {
        val token = getToken()
        if (token == null) return null

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
                if (photo == null) {
                    return@withContext "none"
                }

                // 取 small_file_id
                val fileId = photo.optString("small_file_id")
                if (fileId.isEmpty()) return@withContext null

                val fileUrl = getFileUrl(token, fileId)
                if (fileUrl == null) return@withContext null

                Log.i("AvatarHelper", "Got chat avatar URL for $chatId: $fileUrl")
                return@withContext fileUrl
            } catch (e: Exception) {
                Log.e("AvatarHelper", "Error getting chat avatar for $chatId", e)
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
                    "https://api.telegram.org/file/bot$token/$filePath"
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
