package com.incalr26.botgram.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.card.MaterialCardView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.util.AvatarHelper
import com.incalr26.botgram.util.FileHelper
import com.incalr26.botgram.util.MessageFormatter
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val onClick: ((MessageEntity, View) -> Unit)? = null,
    private val onLongClick: ((MessageEntity, View) -> Boolean)? = null,
    private val onAvatarClick: ((MessageEntity) -> Unit)? = null,
    private val onAvatarLongClick: ((MessageEntity) -> Unit)? = null,
    private val onFileClick: ((MessageEntity) -> Unit)? = null,
    private val onReactionToggle: ((MessageEntity, String) -> Unit)? = null
) : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val minFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun formatSize(size: Long): String {
        if (size <= 0) return "未知"
        if (size < 1024) return "${size} B"
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024f)
        return String.format("%.2f MB", size / (1024f * 1024f))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val systemMessageText: TextView = view.findViewById(R.id.systemMessageText)
        val mainMessageContainer: LinearLayout = view.findViewById(R.id.mainMessageContainer)
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val avatarFallback: TextView = view.findViewById(R.id.avatarFallback)
        val senderName: TextView = view.findViewById(R.id.senderName)
        val bubbleContainer: LinearLayout = view.findViewById(R.id.bubbleContainer)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val messageInfo: TextView = view.findViewById(R.id.messageInfo)
        val forwardInfo: TextView = view.findViewById(R.id.forwardInfo)
        val replyContainer: LinearLayout = view.findViewById(R.id.replyContainer)
        val replyName: TextView = view.findViewById(R.id.replyName)
        val replyMeta: TextView = view.findViewById(R.id.replyMeta)
        val replyPreview: TextView = view.findViewById(R.id.replyPreview)
        val mediaContainer: FrameLayout = view.findViewById(R.id.mediaContainer)
        val mediaImage: ImageView = view.findViewById(R.id.mediaImage)
        val mediaOverlay: FrameLayout = view.findViewById(R.id.mediaOverlay)
        val mediaOverlaySize: TextView = view.findViewById(R.id.mediaOverlaySize)
        val fileContainer: MaterialCardView = view.findViewById(R.id.fileContainer)
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        val fileSizeText: TextView = view.findViewById(R.id.fileSizeText)
        val reactionsContainer: LinearLayout = view.findViewById(R.id.reactionsContainer)
        
        var boundMessageId: Long = 0L
        var loadJob: Job? = null
        var mediaJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)
        val isOutgoing = message.isOutgoing
        val currentMsgId = message.messageId
        holder.boundMessageId = currentMsgId
        val ctx = holder.itemView.context

        val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (e: Exception) { JSONObject() }
        
        // 解析进群或移除等系统事件提示
        if (rawObj.has("new_chat_members") || rawObj.has("left_chat_member") || rawObj.has("pinned_message") || rawObj.has("new_chat_title")) {
            holder.mainMessageContainer.visibility = View.GONE
            holder.systemMessageText.visibility = View.VISIBLE
            val tv = TypedValue(); ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, tv, true)
            holder.systemMessageText.background = GradientDrawable().apply { setColor(tv.data); cornerRadius = 32f }
            var sysTxt = "系统消息"
            val doer = rawObj.optJSONObject("from")?.optString("first_name", "未知") ?: "未知"
            if (rawObj.has("new_chat_members")) {
                val arr = rawObj.getJSONArray("new_chat_members"); val names = mutableListOf<String>()
                for(i in 0 until arr.length()) names.add(arr.getJSONObject(i).optString("first_name", ""))
                sysTxt = "$doer 邀请了 ${names.joinToString(", ")} 加入群组"
            } else if (rawObj.has("left_chat_member")) {
                val left = rawObj.getJSONObject("left_chat_member").optString("first_name", "")
                sysTxt = if (doer == left) "$doer 离开了聊天" else "$doer 移除了 $left"
            } else if (rawObj.has("pinned_message")) { sysTxt = "$doer 置顶了消息"
            } else if (rawObj.has("new_chat_title")) { sysTxt = "$doer 修改群名称为 ${rawObj.getString("new_chat_title")}" }
            holder.systemMessageText.text = sysTxt
            
            // 系统提示绑定长按菜单与发起人详情页跳转
            holder.systemMessageText.setOnLongClickListener { v -> onLongClick?.invoke(message, v) ?: false }
            holder.systemMessageText.setOnClickListener { onAvatarClick?.invoke(message) }
            return
        }

        holder.mainMessageContainer.visibility = View.VISIBLE
        holder.systemMessageText.visibility = View.GONE
        Coil.imageLoader(ctx).enqueue(ImageRequest.Builder(ctx).data(null).target(holder.mediaImage).build())

        if (isOutgoing) {
            holder.avatar.visibility = View.GONE
            holder.avatarFallback.visibility = View.GONE
            holder.mainMessageContainer.layoutDirection = View.LAYOUT_DIRECTION_RTL
            holder.bubbleContainer.background = ctx.getDrawable(R.drawable.outgoing_bg)
        } else {
            holder.mainMessageContainer.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.bubbleContainer.background = ctx.getDrawable(R.drawable.incoming_bg)
            holder.avatarFallback.text = message.senderName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatar.visibility = View.GONE
            
            val clickAvatar = View.OnClickListener { onAvatarClick?.invoke(message) }
            val longClickAvatar = View.OnLongClickListener { onAvatarLongClick?.invoke(message); true }
            holder.avatar.setOnClickListener(clickAvatar)
            holder.avatar.setOnLongClickListener(longClickAvatar)
            holder.avatarFallback.setOnClickListener(clickAvatar)
            holder.avatarFallback.setOnLongClickListener(longClickAvatar)
        }

        val nameBuilder = StringBuilder(message.senderName ?: "未知")
        val role = message.senderRole ?: ""
        if (role == "creator" || role == "administrator" || role == "owner") nameBuilder.append(" [管理]")
        holder.senderName.text = nameBuilder.toString()

        if (rawObj.has("forward_origin") || rawObj.has("forward_from") || rawObj.has("forward_from_chat")) {
            val origin = rawObj.optJSONObject("forward_origin")
            val fName = origin?.optJSONObject("sender_user")?.optString("first_name") ?: origin?.optJSONObject("chat")?.optString("title") ?: rawObj.optJSONObject("forward_from")?.optString("first_name") ?: rawObj.optJSONObject("forward_from_chat")?.optString("title") ?: "未知"
            holder.forwardInfo.text = "转发自 $fName"
            holder.forwardInfo.visibility = View.VISIBLE
        } else holder.forwardInfo.visibility = View.GONE

        if (!message.replyToJson.isNullOrEmpty()) {
            try {
                val replyMsg = JSONObject(message.replyToJson)
                holder.replyName.text = replyMsg.optJSONObject("from")?.optString("first_name", "未知")
                holder.replyPreview.text = replyMsg.optString("text", "[媒体消息]")
                holder.replyContainer.visibility = View.VISIBLE
            } catch (e: Exception) { holder.replyContainer.visibility = View.GONE }
        } else holder.replyContainer.visibility = View.GONE

        holder.mediaContainer.visibility = View.GONE
        holder.fileContainer.visibility = View.GONE
        holder.mediaJob?.cancel()
        
        val prefs = ctx.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("bot_token", "") ?: ""
        val unlockedSet = prefs.getStringSet("unlocked_media", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        var actualText = message.text ?: ""; var mediaLabel = ""

        if (rawObj.has("photo") || rawObj.has("sticker") || rawObj.has("video")) {
            holder.mediaContainer.visibility = View.VISIBLE
            var fileId = ""; var fileSize = 0L; var autoCache = false
            if (rawObj.has("photo")) {
                actualText = rawObj.optString("caption", ""); mediaLabel = "[图片] "
                val arr = rawObj.getJSONArray("photo"); val photoObj = arr.getJSONObject(arr.length() - 1)
                fileId = photoObj.getString("file_id"); fileSize = photoObj.optLong("file_size", 0L); autoCache = prefs.getBoolean("auto_image", false)
            } else if (rawObj.has("sticker")) {
                actualText = ""; val stickerObj = rawObj.getJSONObject("sticker")
                mediaLabel = "[贴纸] "; fileId = stickerObj.getString("file_id"); fileSize = stickerObj.optLong("file_size", 0L); autoCache = prefs.getBoolean("auto_sticker", false)
            } else if (rawObj.has("video")) {
                actualText = rawObj.optString("caption", ""); mediaLabel = "[视频] "; val vid = rawObj.getJSONObject("video")
                fileId = vid.optJSONObject("thumbnail")?.optString("file_id") ?: vid.getString("file_id")
                fileSize = vid.optLong("file_size", 0L); autoCache = false 
            }
            holder.mediaOverlaySize.text = formatSize(fileSize)
            val isUnlocked = isOutgoing || unlockedSet.contains(currentMsgId.toString())
            holder.mediaOverlay.visibility = if (autoCache || isUnlocked) View.GONE else View.VISIBLE

            val loadMedia = {
                holder.mediaOverlay.visibility = View.GONE
                if (!isOutgoing) { unlockedSet.add(currentMsgId.toString()); prefs.edit().putStringSet("unlocked_media", unlockedSet).apply() }
                holder.mediaJob = scope.launch {
                    val url = FileHelper.getTelegramFileUrl(fileId, token)
                    if (holder.boundMessageId == currentMsgId && !url.isNullOrEmpty()) {
                        Coil.imageLoader(ctx).enqueue(ImageRequest.Builder(ctx).data(url).target(holder.mediaImage).crossfade(true).build())
                    }
                }
            }
            if (autoCache || isUnlocked) loadMedia()
            holder.mediaOverlay.setOnClickListener { loadMedia() }
        } else if (rawObj.has("document") || rawObj.has("audio") || rawObj.has("voice")) {
            val isAudio = rawObj.has("audio") || rawObj.has("voice")
            actualText = rawObj.optString("caption", ""); mediaLabel = if (isAudio) "[音频] " else "[文件] "
            holder.fileContainer.visibility = View.VISIBLE
            val doc = if (isAudio) (if (rawObj.has("audio")) rawObj.getJSONObject("audio") else rawObj.getJSONObject("voice")) else rawObj.getJSONObject("document")
            holder.fileNameText.text = doc.optString("file_name", if (isAudio) "音频消息" else "未知文件")
            holder.fileSizeText.text = formatSize(doc.optLong("file_size", 0L))
            holder.fileContainer.setOnClickListener { onFileClick?.invoke(message) }
        }

        if (actualText.isEmpty() && !message.isDeleted) holder.messageText.visibility = View.GONE
        else {
            holder.messageText.visibility = View.VISIBLE
            val builder = SpannableStringBuilder(MessageFormatter.format(actualText, message.entities) ?: "")
            if (message.isDeleted) {
                val start = builder.length; builder.append(" [已撤回]")
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#D32F2F")), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            holder.messageText.text = builder
        }

        // 解析多项表情回应并判断自身是否已激活高亮
        holder.reactionsContainer.removeAllViews()
        if (!message.reactions.isNullOrEmpty() && message.reactions != "[]") {
            try {
                val arr = JSONArray(message.reactions)
                if (arr.length() > 0) {
                    holder.reactionsContainer.visibility = View.VISIBLE
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val emojiStr = if (r.has("type") && r.optJSONObject("type")?.optString("type") == "emoji") r.getJSONObject("type").optString("emoji", "❓") else r.optString("emoji", "❓")
                        val count = if (r.has("total_count")) r.getInt("total_count") else r.optInt("count", 1)
                        val isChosen = r.optBoolean("chosen", false) // 判断自身是否已回应
                        
                        val tv = TextView(ctx).apply {
                            text = "$emojiStr $count"
                            textSize = 12f
                            val bgAttr = if (isChosen) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurfaceVariant
                            val textAttr = if (isChosen) com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSurfaceVariant
                            setTextColor(ctx.getColorAttr(textAttr))
                            background = GradientDrawable().apply { 
                                setColor(ctx.getColorAttr(bgAttr))
                                cornerRadius = 24f
                                if (isChosen) setStroke(2, ctx.getColorAttr(com.google.android.material.R.attr.colorPrimary))
                            }
                            setPadding(16, 8, 16, 8)
                            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 12 }
                            setOnClickListener { onReactionToggle?.invoke(message, emojiStr) }
                        }
                        holder.reactionsContainer.addView(tv)
                    }
                } else holder.reactionsContainer.visibility = View.GONE
            } catch (e: Exception) { holder.reactionsContainer.visibility = View.GONE }
        } else holder.reactionsContainer.visibility = View.GONE

        val dateStr = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(message.date * 1000))
        val editStr = if (message.isEdited) { val ed = rawObj.optLong("edit_date", 0L); if (ed > 0) " [已编辑 ${minFormat.format(Date(ed * 1000))}]" else " [已编辑]" } else ""
        holder.messageInfo.text = "$mediaLabel ID:${message.messageId}  $dateStr ${timeFormat.format(Date(message.date * 1000))}$editStr"

        holder.loadJob?.cancel()
        if (!isOutgoing && prefs.getBoolean("use_real_avatar", true) && message.senderUserId != null) {
            holder.loadJob = scope.launch {
                if (holder.boundMessageId != currentMsgId) return@launch
                val url = AvatarHelper.getUserAvatar(message.senderUserId)
                if (!url.isNullOrEmpty()) {
                    Coil.imageLoader(ctx).enqueue(ImageRequest.Builder(ctx).data(url).crossfade(true).transformations(CircleCropTransformation()).listener(onSuccess = { _, res ->
                        if (holder.boundMessageId == currentMsgId) { holder.avatar.setImageDrawable(res.drawable); holder.avatarFallback.visibility = View.GONE; holder.avatar.visibility = View.VISIBLE }
                    }).build())
                }
            }
        }
        
        holder.mainMessageContainer.setOnLongClickListener { v -> if (!message.isDeleted) onLongClick?.invoke(message, v) ?: false else true }
        holder.bubbleContainer.setOnClickListener { v -> if (!message.isDeleted) onClick?.invoke(message, v) }
    }
    
    private fun Context.getColorAttr(attr: Int): Int { val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId
        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem == newItem
    }
}
