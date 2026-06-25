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
import com.incalr26.botgram.util.NetworkStateHolder
import com.incalr26.botgram.util.NewMessageNotifier
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class PollingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private var isRunning = false
    data class MemberInfo(val role: String, val title: String)
    private val memberCache = ConcurrentHashMap<String, MemberInfo>()

    override fun onCreate() {
        super.onCreate()
        chatRepository = ChatRepository(BotApp.instance.databaseHelper)
        messageRepository = MessageRepository(BotApp.instance.databaseHelper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1001, notification)
        if (!isRunning) { isRunning = true; serviceScope.launch { startPolling() } }
        return START_STICKY
    }

    private suspend fun startPolling() {
        val prefs = getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("bot_token", null) ?: return
        var offset = prefs.getLong("update_offset", 0L)

        while (isRunning) {
            try {
                // 完美包含表情的抓取
                val url = "https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=30&allowed_updates=[\"message\",\"edited_message\",\"message_reaction\",\"message_reaction_count\"]"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    NetworkStateHolder.updateState(true)
                    val body = response.body?.string() ?: continue
                    val json = JSONObject(body)
                    if (json.getBoolean("ok")) {
                        val results = json.getJSONArray("result")
                        for (i in 0 until results.length()) {
                            val update = results.getJSONObject(i)
                            offset = update.getLong("update_id") + 1
                            prefs.edit().putLong("update_offset", offset).apply()
                            
                            if (update.has("message")) processMessage(update.getJSONObject("message"))
                            else if (update.has("edited_message")) processEditedMessage(update.getJSONObject("edited_message"))
                            else if (update.has("message_reaction")) processMessageReaction(update.getJSONObject("message_reaction"))
                            else if (update.has("message_reaction_count")) processReactionCount(update.getJSONObject("message_reaction_count"))
                        }
                    }
                } else NetworkStateHolder.updateState(false)
                delay(1000)
            } catch (e: Exception) { NetworkStateHolder.updateState(false); delay(5000) }
        }
    }

    private suspend fun processEditedMessage(msg: JSONObject) {
        val chatId = msg.getJSONObject("chat").getLong("id")
        val messageId = msg.getLong("message_id")
        val existing = messageRepository.getMessage(chatId, messageId) ?: return
        
        val newText = extractMessageText(msg)
        val historyArray = if (existing.editHistory != null) JSONArray(existing.editHistory) else JSONArray()
        
        if (existing.text != null && existing.text != newText) {
            val histObj = JSONObject()
            histObj.put("text", existing.text)
            // 记录这一次被覆盖文本的时间
            histObj.put("date", msg.optLong("edit_date", System.currentTimeMillis() / 1000))
            historyArray.put(histObj)
        }
        
        val updated = existing.copy(
            text = newText,
            isEdited = true,
            editHistory = historyArray.toString(),
            rawJson = msg.toString(),
            entities = if (msg.has("entities")) msg.getJSONArray("entities").toString() else existing.entities
        )
        messageRepository.insertMessage(updated)
        NewMessageNotifier.newMessage.postValue(Unit)
    }

    private suspend fun processMessageReaction(mr: JSONObject) {
        val chatId = mr.getJSONObject("chat").getLong("id")
        val messageId = mr.getLong("message_id")
        val newReactions = mr.getJSONArray("new_reaction")
        
        val reactArr = JSONArray()
        val emojiMap = mutableMapOf<String, Int>()
        for (i in 0 until newReactions.length()) {
            val typeObj = newReactions.getJSONObject(i)
            if (typeObj.getString("type") == "emoji") {
                val e = typeObj.getString("emoji")
                emojiMap[e] = emojiMap.getOrDefault(e, 0) + 1
            }
        }
        for ((k, v) in emojiMap) {
            val obj = JSONObject().apply { put("emoji", k); put("count", v) }
            reactArr.put(obj)
        }
        
        val existing = messageRepository.getMessage(chatId, messageId) ?: return
        if (reactArr.length() > 0) {
             messageRepository.insertMessage(existing.copy(reactions = reactArr.toString()))
             NewMessageNotifier.newMessage.postValue(Unit)
        }
    }

    private suspend fun processReactionCount(mrc: JSONObject) {
        val chatId = mrc.getJSONObject("chat").getLong("id")
        val messageId = mrc.getLong("message_id")
        val reactions = mrc.getJSONArray("reactions")
        val reactArr = JSONArray()
        for (i in 0 until reactions.length()) {
            val r = reactions.getJSONObject(i)
            val typeObj = r.getJSONObject("type")
            if (typeObj.getString("type") == "emoji") {
                val obj = JSONObject().apply { put("emoji", typeObj.getString("emoji")); put("count", r.getInt("total_count")) }
                reactArr.put(obj)
            }
        }
        val existing = messageRepository.getMessage(chatId, messageId) ?: return
        messageRepository.insertMessage(existing.copy(reactions = reactArr.toString()))
        NewMessageNotifier.newMessage.postValue(Unit)
    }

    private suspend fun processMessage(msg: JSONObject) {
        val chatObj = msg.getJSONObject("chat")
        val chatId = chatObj.getLong("id")
        val date = msg.getLong("date") * 1000
        val messageText = extractMessageText(msg)

        val from = msg.optJSONObject("from")
        val senderId = if (from != null && from.has("id")) from.getLong("id") else null

        // 防止覆盖掉自己在手机上本地提前发送并插表的记录
        val existingMsg = messageRepository.getMessage(chatId, msg.getLong("message_id"))
        val isOutgoing = existingMsg?.isOutgoing ?: false

        val existingChat = chatRepository.getChatById(chatId)
        val chatEntity = ChatEntity(
            chatId = chatId, type = chatObj.getString("type"), title = chatObj.optString("title", null),
            firstName = chatObj.optString("first_name", null), lastName = chatObj.optString("last_name", null),
            username = chatObj.optString("username", null), lastMessage = messageText, lastTime = date,
            unreadCount = if (isOutgoing) 0 else 1, avatarUrl = existingChat?.avatarUrl
        )
        chatRepository.insertOrUpdateChat(chatEntity)

        val messageEntity = MessageEntity(
            messageId = msg.getLong("message_id"), chatId = chatId, senderUserId = senderId,
            senderName = from?.optString("first_name") ?: "未知", text = messageText, date = msg.getLong("date"),
            isOutgoing = isOutgoing, rawJson = msg.toString(),
            entities = if (msg.has("entities")) msg.getJSONArray("entities").toString() else null,
            replyToJson = if (msg.has("reply_to_message")) msg.getJSONObject("reply_to_message").toString() else null,
            senderRole = existingMsg?.senderRole, senderTitle = existingMsg?.senderTitle,
            reactions = existingMsg?.reactions
        )
        messageRepository.insertMessage(messageEntity)
        NewMessageNotifier.newMessage.postValue(Unit)
    }

    private fun extractMessageText(msg: JSONObject): String? {
        if (msg.has("text")) return msg.getString("text")
        val caption = msg.optString("caption", null)
        val captionSuffix = if (!caption.isNullOrEmpty()) " $caption" else ""
        return when {
            msg.has("sticker") -> "贴纸$captionSuffix"
            msg.has("photo") -> "[图片]$captionSuffix"
            msg.has("document") -> "[文件]$captionSuffix"
            msg.has("video") -> "[视频]$captionSuffix"
            msg.has("audio") -> "[音频]$captionSuffix"
            msg.has("voice") -> "[语音]$captionSuffix"
            else -> "[媒体消息]$captionSuffix"
        }
    }

    private fun createNotification(): Notification {
        val channelId = "botgram_polling"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channelId, "消息监听", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId).setContentTitle("Botgram").setContentText("监听中").setSmallIcon(R.drawable.ic_launcher_foreground).build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { isRunning = false; serviceScope.cancel(); super.onDestroy() }
}
