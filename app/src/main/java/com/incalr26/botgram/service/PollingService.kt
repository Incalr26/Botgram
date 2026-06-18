package com.incalr26.botgram.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.ChatEntity
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.data.repository.ChatRepository
import com.incalr26.botgram.data.repository.MessageRepository
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject

class PollingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        chatRepository = ChatRepository(BotApp.instance.databaseHelper)
        messageRepository = MessageRepository(BotApp.instance.databaseHelper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1001, notification)

        if (!isRunning) {
            isRunning = true
            serviceScope.launch {
                startPolling()
            }
        }
        return START_STICKY
    }

    private suspend fun startPolling() {
        val prefs = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("bot_token", null) ?: return
        var offset = prefs.getLong("update_offset", 0L)

        while (isRunning) {
            try {
                val url = "https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=30"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val json = JSONObject(body)
                    if (json.getBoolean("ok")) {
                        val results = json.getJSONArray("result")
                        for (i in 0 until results.length()) {
                            val update = results.getJSONObject(i)
                            val updateId = update.getLong("update_id")
                            offset = updateId + 1
                            prefs.edit().putLong("update_offset", offset).apply()
                            if (update.has("message")) {
                                processMessage(update.getJSONObject("message"))
                            }
                        }
                    }
                }
                delay(1000)
            } catch (e: Exception) {
                delay(5000)
            }
        }
    }

    private suspend fun processMessage(msg: JSONObject) {
        val chatObj = msg.getJSONObject("chat")
        val chatId = chatObj.getLong("id")
        val chatType = chatObj.getString("type")
        val chatTitle = chatObj.optString("title", null)
        val chatFirstName = chatObj.optString("first_name", null)
        val chatLastName = chatObj.optString("last_name", null)
        val chatUsername = chatObj.optString("username", null)
        val date = msg.getLong("date") * 1000

        // 处理系统消息或普通文本
        val messageText = extractMessageText(msg)
        val entitiesJson = if (msg.has("entities")) {
            msg.getJSONArray("entities").toString()
        } else null

        // 更新会话
        chatRepository.insertOrUpdateChat(
            ChatEntity(
                chatId = chatId,
                type = chatType,
                title = chatTitle,
                firstName = chatFirstName,
                lastName = chatLastName,
                username = chatUsername,
                lastMessage = messageText,
                lastTime = date,
                unreadCount = 1
            )
        )

        // 获取发送者信息
        val from = msg.optJSONObject("from")
        val senderId = from?.optLong("id")
        val senderName = from?.optString("first_name") ?: "未知"
        val messageId = msg.getLong("message_id")

        messageRepository.insertMessage(
            MessageEntity(
                messageId = messageId,
                chatId = chatId,
                senderUserId = senderId,
                senderName = senderName,
                text = messageText,
                date = msg.getLong("date"),
                isOutgoing = false,
                rawJson = msg.toString(),
                entities = entitiesJson
            )
        )

        // 广播新消息
        val refreshIntent = Intent("com.incalr26.botgram.NEW_MESSAGE")
        refreshIntent.putExtra("chatId", chatId)
        sendBroadcast(refreshIntent)
    }

    private fun extractMessageText(msg: JSONObject): String? {
        // 系统消息类型
        if (msg.has("new_chat_members")) {
            val members = msg.getJSONArray("new_chat_members")
            val names = mutableListOf<String>()
            for (i in 0 until members.length()) {
                val user = members.getJSONObject(i)
                names.add(user.optString("first_name", "用户"))
            }
            return names.joinToString(", ") + " 加入了群组"
        }
        if (msg.has("left_chat_member")) {
            val user = msg.getJSONObject("left_chat_member")
            val name = user.optString("first_name", "用户")
            return "$name 离开了群组"
        }
        if (msg.has("group_chat_created")) {
            return "群组已创建"
        }
        if (msg.has("new_chat_title")) {
            return "群组名称已更改为：“${msg.getString("new_chat_title")}”"
        }

        // 普通文本
        if (msg.has("text")) return msg.getString("text")

        // 贴纸、图片等媒体
        if (msg.has("sticker")) {
            val sticker = msg.getJSONObject("sticker")
            val emoji = sticker.optString("emoji", "")
            return if (emoji.isNotEmpty()) "贴纸 $emoji" else "贴纸"
        }
        if (msg.has("photo")) return "[图片]"
        if (msg.has("document")) return "[文件]"
        if (msg.has("video")) return "[视频]"
        if (msg.has("audio") || msg.has("voice")) return "[语音]"

        return "[媒体消息]"
    }

    private fun createNotification(): Notification {
        val channelId = "botgram_polling"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "消息监听", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Botgram 运行中")
            .setContentText("正在监听新消息")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
