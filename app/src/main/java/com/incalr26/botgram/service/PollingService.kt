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
import org.json.JSONArray

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

        val lastMessageText = extractText(msg)
        chatRepository.insertOrUpdateChat(
            ChatEntity(
                chatId = chatId,
                type = chatType,
                title = chatTitle,
                firstName = chatFirstName,
                lastName = chatLastName,
                username = chatUsername,
                lastMessage = lastMessageText,
                lastTime = msg.getLong("date") * 1000,
                unreadCount = 1
            )
        )

        val messageId = msg.getLong("message_id")
        val from = msg.optJSONObject("from")
        val senderId = from?.optLong("id")
        val senderName = from?.optString("first_name") ?: "未知"
        val text = msg.optString("text", null)

        // 提取 entities JSON 字符串
        val entitiesJson = if (msg.has("entities")) {
            msg.getJSONArray("entities").toString()
        } else null

        messageRepository.insertMessage(
            MessageEntity(
                messageId = messageId,
                chatId = chatId,
                senderUserId = senderId,
                senderName = senderName,
                text = text,
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

    private fun extractText(msg: JSONObject): String? {
        return msg.optString("text", null)
            ?: msg.optJSONObject("photo")?.let { "[图片]" }
            ?: msg.optJSONObject("sticker")?.let { "[贴纸]" }
            ?: msg.optJSONObject("document")?.let { "[文件]" }
            ?: "[媒体消息]"
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
