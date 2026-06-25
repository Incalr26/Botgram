package com.incalr26.botgram.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private val onClick: ((MessageEntity) -> Unit)? = null,
    private val onLongClick: ((MessageEntity, View) -> Boolean)? = null,
    private val onFileClick: ((MessageEntity) -> Unit)? = null
) : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    private val shortDateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val minFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun formatSize(size: Long): String {
        if (size <= 0) return "未知大小"
        if (size < 1024) return "${size} B"
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024f)
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024f * 1024f))
        return String.format("%.2f GB", size / (1024f * 1024f * 1024f))
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
        val mediaContainer: MaterialCardView = view.findViewById(R.id.mediaContainer)
        val mediaImage: ImageView = view.findViewById(R.id.mediaImage)
        val mediaOverlay: View = view.findViewById(R.id.mediaOverlay)
        val mediaOverlayIcon: ImageView = view.findViewById(R.id.mediaOverlayIcon)
        val mediaOverlaySize: TextView = view.findViewById(R.id.mediaOverlaySize)
        val fileContainer: MaterialCardView = view.findViewById(R.id.fileContainer)
        val fileIcon: ImageView = view.findViewById(R.id.fileIcon)
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        val fileSizeText: TextView = view.findViewById(R.id.fileSizeText)
        val reactionsContainer: LinearLayout = view.findViewById(R.id.reactionsContainer)
        
        val container: LinearLayout = view as LinearLayout
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
        
        // --- 系统消息拦截与渲染 ---
        if (rawObj.has("new_chat_members") || rawObj.has("left_chat_member") || rawObj.has("pinned_message") || rawObj.has("new_chat_title")) {
            holder.mainMessageContainer.visibility = View.GONE
            holder.systemMessageText.visibility = View.VISIBLE
            
            val tv = TypedValue(); ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, tv, true)
            holder.systemMessageText.background = GradientDrawable().apply { setColor(tv.data); cornerRadius = 32f * ctx.resources.displayMetrics.density }
            
            var sysTxt = "系统消息"
            val doer = rawObj.optJSONObject("from")?.optString("first_name", "未知用户") ?: "未知"
            if (rawObj.has("new_chat_members")) {
                val arr = rawObj.getJSONArray("new_chat_members"); val names = mutableListOf<String>()
                for(i in 0 until arr.length()) names.add(arr.getJSONObject(i).optString("first_name", ""))
                sysTxt = "$doer 邀请了 ${names.joinToString(", ")} 加入群组"
            } else if (rawObj.has("left_chat_member")) {
                val left = rawObj.getJSONObject("left_chat_member").optString("first_name", "")
                sysTxt = if (doer == left) "$doer 离开了群组" else "$doer 移除了 $left"
            } else if (rawObj.has("pinned_message")) { sysTxt = "$doer 置顶了一条消息"
            } else if (rawObj.has("new_chat_title")) { sysTxt = "$doer 修改了群组名为 ${rawObj.getString("new_chat_title")}" }
            
            holder.systemMessageText.text = sysTxt
            return
        }

        holder.mainMessageContainer.visibility = View.VISIBLE
        holder.systemMessageText.visibility = View.GONE

        val clearReq = ImageRequest.Builder(ctx).data(null).target(holder.mediaImage).build()
        Coil.imageLoader(ctx).enqueue(clearReq)
        holder.mediaImage.setImageDrawable(null)

        if (isOutgoing) {
            holder.avatar.visibility = View.GONE
            holder.avatarFallback.visibility = View.GONE
            holder.mainMessageContainer.layoutDirection = View.LAYOUT_DIRECTION_RTL
            holder.bubbleContainer.background = ctx.getDrawable(R.drawable.outgoing_bg)
        } else {
            holder.mainMessageContainer.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.bubbleContainer.background = ctx.getDrawable(R.drawable.incoming_bg)
            val fallback = message.senderName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatarFallback.text = fallback
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatar.visibility = View.GONE
            
            val openInfo = { message.senderUserId?.let { id -> ctx.startActivity(Intent(ctx, ChatInfoActivity::class.java).apply { putExtra("chatId", id) }) } }
            holder.avatar.setOnClickListener { openInfo() }
            holder.avatarFallback.setOnClickListener { openInfo() }
        }

        val nameBuilder = StringBuilder(message.senderName ?: "未知")
        val role = message.senderRole ?: ""
        val title = message.senderTitle
        if (role == "creator" || role == "administrator" || role == "owner") {
            val roleTag = if (role == "creator" || role == "owner") "群主" else "管理员"
            nameBuilder.append(if (!title.isNullOrBlank()) " [$roleTag $title]" else " [$roleTag]")
        } else if (!title.isNullOrBlank()) nameBuilder.append(" [$title]")
        holder.senderName.text = nameBuilder.toString()

        if (rawObj.has("forward_origin") || rawObj.has("forward_from") || rawObj.has("forward_from_chat")) {
            val origin = rawObj.optJSONObject("forward_origin")
            val fName = origin?.optJSONObject("sender_user")?.optString("first_name") 
                        ?: origin?.optJSONObject("chat")?.optString("title") 
                        ?: rawObj.optJSONObject("forward_from")?.optString("first_name") 
                        ?: rawObj.optJSONObject("forward_from_chat")?.optString("title") ?: "未知"
            val fDate = origin?.optLong("date", 0L) ?: rawObj.optLong("forward_date", 0L)
            holder.forwardInfo.text = "转发自 $fName " + (if (fDate > 0) shortDateFormat.format(Date(fDate * 1000)) else "")
            holder.forwardInfo.visibility = View.VISIBLE
        } else holder.forwardInfo.visibility = View.GONE

        if (!message.replyToJson.isNullOrEmpty()) {
            try {
                val replyMsg = JSONObject(message.replyToJson)
                holder.replyName.text = replyMsg.optJSONObject("from")?.optString("first_name", "未知")
                holder.replyPreview.text = replyMsg.optString("text", "[媒体消息]")
                val rDate = replyMsg.optLong("date", 0L)
                val dStr = if (rDate > 0) shortDateFormat.format(Date(rDate * 1000)) else ""
                holder.replyMeta.text = if (replyMsg.optLong("message_id", 0L) > 0) "ID:${replyMsg.optLong("message_id")} $dStr" else dStr
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
            var fileId = ""; var fileSize = 0L; var isVideo = false; var autoCache = false
            if (rawObj.has("photo")) {
                actualText = rawObj.optString("caption", ""); mediaLabel = "[图片] "
                val arr = rawObj.getJSONArray("photo"); val photoObj = arr.getJSONObject(arr.length() - 1)
                fileId = photoObj.getString("file_id"); fileSize = photoObj.optLong("file_size", 0L); autoCache = prefs.getBoolean("auto_image", false)
            } else if (rawObj.has("sticker")) {
                actualText = ""; val stickerObj = rawObj.getJSONObject("sticker")
                mediaLabel = if (stickerObj.optString("emoji", "").isNotEmpty()) "[贴纸 ${stickerObj.optString("emoji")}] " else "[贴纸] "
                fileId = stickerObj.getString("file_id"); fileSize = stickerObj.optLong("file_size", 0L); autoCache = prefs.getBoolean("auto_sticker", false)
            } else if (rawObj.has("video")) {
                actualText = rawObj.optString("caption", ""); mediaLabel = "[视频] "; val vid = rawObj.getJSONObject("video")
                fileId = vid.optJSONObject("thumbnail")?.optString("file_id") ?: vid.getString("file_id")
                fileSize = vid.optLong("file_size", 0L); isVideo = true; autoCache = false 
            }
            holder.mediaOverlaySize.text = formatSize(fileSize)
            holder.mediaOverlayIcon.setImageResource(if (isVideo) android.R.drawable.ic_media_play else android.R.drawable.stat_sys_download)
            val isUnlocked = isOutgoing || unlockedSet.contains(currentMsgId.toString())
            holder.mediaOverlay.visibility = if (autoCache || isUnlocked) View.GONE else View.VISIBLE

            val loadMedia = {
                holder.mediaOverlay.visibility = View.GONE
                if (!isOutgoing) { unlockedSet.add(currentMsgId.toString()); prefs.edit().putStringSet("unlocked_media", unlockedSet).apply() }
                holder.mediaJob = scope.launch {
                    val url = FileHelper.getTelegramFileUrl(fileId, token)
                    if (holder.boundMessageId == currentMsgId && !url.isNullOrEmpty()) {
                        if (isVideo && !rawObj.getJSONObject("video").has("thumbnail")) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), "video/*") }) } catch (_: Exception) { }
                        } else Coil.imageLoader(ctx).enqueue(ImageRequest.Builder(ctx).data(url).target(holder.mediaImage).crossfade(true).build())
                    }
                }
            }
            if (autoCache || isUnlocked) loadMedia()
            holder.mediaOverlay.setOnClickListener { loadMedia() }
            holder.mediaImage.setOnClickListener {
                if (isVideo) {
                    holder.mediaJob = scope.launch {
                        val vUrl = FileHelper.getTelegramFileUrl(rawObj.getJSONObject("video").getString("file_id"), token)
                        if (!vUrl.isNullOrEmpty()) try { ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(vUrl), "video/*") }) } catch (_: Exception) {}
                    }
                }
            }
        } else if (rawObj.has("document") || rawObj.has("audio") || rawObj.has("voice")) {
            val isAudio = rawObj.has("audio") || rawObj.has("voice")
            actualText = rawObj.optString("caption", ""); mediaLabel = if (isAudio) "[音频] " else "[文件] "
            holder.fileContainer.visibility = View.VISIBLE
            val doc = if (isAudio) (if (rawObj.has("audio")) rawObj.getJSONObject("audio") else rawObj.getJSONObject("voice")) else rawObj.getJSONObject("document")
            holder.fileIcon.setImageResource(if (isAudio) android.R.drawable.ic_media_play else R.drawable.ic_file_document)
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
            val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
            for (urlSpan in urlSpans) {
                val start = builder.getSpanStart(urlSpan); val end = builder.getSpanEnd(urlSpan); builder.removeSpan(urlSpan)
                builder.setSpan(object : ClickableSpan() {
                    override fun onClick(w: View) { MaterialAlertDialogBuilder(ctx).setTitle("打开链接").setMessage(urlSpan.url).setPositiveButton("打开") { _, _ -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlSpan.url))) }.setNegativeButton("取消", null).show() }
                    override fun updateDrawState(ds: TextPaint) { super.updateDrawState(ds); ds.isUnderlineText = true }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            holder.messageText.text = builder; holder.messageText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }

        // --- 修复并发表情重叠：彻底兼容 Telegram 原生 Reaction 嵌套结构 ---
        if (!message.reactions.isNullOrEmpty() && message.reactions != "[]") {
            try {
                val arr = JSONArray(message.reactions)
                if (arr.length() > 0) {
                    holder.reactionsContainer.visibility = View.VISIBLE
                    holder.reactionsContainer.removeAllViews()
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        // 兼容 Telegram 原生结构：type -> {type: emoji, emoji: '👍'}
                        val emojiStr = if (r.has("type") && r.optJSONObject("type")?.optString("type") == "emoji") {
                            r.getJSONObject("type").optString("emoji", "❓")
                        } else r.optString("emoji", "❓")
                        val count = if (r.has("total_count")) r.getInt("total_count") else r.optInt("count", 1)
                        
                        val tv = TextView(ctx).apply {
                            text = "$emojiStr $count"
                            textSize = 12f; setTextColor(ctx.getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                            background = GradientDrawable().apply { setColor(ctx.getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant)); cornerRadius = 24f; setStroke(1, Color.LTGRAY) }
                            setPadding(16, 6, 16, 6); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 12 }
                        }
                        holder.reactionsContainer.addView(tv)
                    }
                } else holder.reactionsContainer.visibility = View.GONE
            } catch (e: Exception) { holder.reactionsContainer.visibility = View.GONE }
        } else holder.reactionsContainer.visibility = View.GONE

        val now = Calendar.getInstance(); val msgCal = Calendar.getInstance().apply { timeInMillis = message.date * 1000 }
        val dateStr = if (msgCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(message.date * 1000)) else SimpleDateFormat("yy年MM月dd日", Locale.getDefault()).format(Date(message.date * 1000))
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
    }
    
    private fun Context.getColorAttr(attr: Int): Int { val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId
        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem == newItem
    }
}
