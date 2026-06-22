package com.incalr26.botgram.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.util.AvatarHelper
import com.incalr26.botgram.util.FileHelper
import com.incalr26.botgram.util.MessageFormatter
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val onClick: ((MessageEntity) -> Unit)? = null,
    private val onLongClick: ((MessageEntity, View) -> Boolean)? = null
) : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    private val shortDateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
        val mediaOverlayIcon: ImageView = view.findViewById(R.id.mediaOverlayIcon)
        
        val fileContainer: LinearLayout = view.findViewById(R.id.fileContainer)
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        val fileSizeText: TextView = view.findViewById(R.id.fileSizeText)
        
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

        if (isOutgoing) {
            holder.avatar.visibility = View.GONE
            holder.avatarFallback.visibility = View.GONE
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_RTL
            holder.bubbleContainer.background = ctx.getDrawable(R.drawable.outgoing_bg)
        } else {
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.bubbleContainer.background = ctx.getDrawable(R.drawable.incoming_bg)
            val senderName = message.senderName ?: "?"
            val fallback = senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatarFallback.text = fallback
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatar.visibility = View.GONE
            holder.avatar.setImageDrawable(null)
        }

        val nameBuilder = StringBuilder(message.senderName ?: "未知")
        val role = message.senderRole ?: ""
        val title = message.senderTitle
        val isAdmin = role == "creator" || role == "administrator" || role == "owner"
        if (isAdmin) {
            val roleTag = if (role == "creator" || role == "owner") "群主" else "管理员"
            nameBuilder.append(if (!title.isNullOrBlank()) " [$roleTag $title]" else " [$roleTag]")
        } else if (!title.isNullOrBlank()) {
            nameBuilder.append(" [$title]")
        }
        holder.senderName.text = nameBuilder.toString()

        val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (e: Exception) { JSONObject() }
        
        // 渲染转发标记
        if (rawObj.has("forward_origin")) {
            val origin = rawObj.getJSONObject("forward_origin")
            val fName = origin.optJSONObject("sender_user")?.optString("first_name") ?: origin.optJSONObject("chat")?.optString("title") ?: "未知"
            val fDate = origin.optLong("date", 0L)
            holder.forwardInfo.text = "转发自 $fName " + (if (fDate > 0) shortDateFormat.format(Date(fDate * 1000)) else "")
            holder.forwardInfo.visibility = View.VISIBLE
        } else if (rawObj.has("forward_from") || rawObj.has("forward_from_chat")) {
            val fName = rawObj.optJSONObject("forward_from")?.optString("first_name") ?: rawObj.optJSONObject("forward_from_chat")?.optString("title") ?: "未知"
            val fDate = rawObj.optLong("forward_date", 0L)
            holder.forwardInfo.text = "转发自 $fName " + (if (fDate > 0) shortDateFormat.format(Date(fDate * 1000)) else "")
            holder.forwardInfo.visibility = View.VISIBLE
        } else {
            holder.forwardInfo.visibility = View.GONE
        }

        // 渲染回复标记
        if (!message.replyToJson.isNullOrEmpty()) {
            try {
                val replyMsg = JSONObject(message.replyToJson)
                holder.replyName.text = replyMsg.optJSONObject("from")?.optString("first_name", "未知")
                holder.replyPreview.text = replyMsg.optString("text", "[媒体消息]")
                val rDate = replyMsg.optLong("date", 0L)
                val rId = replyMsg.optLong("message_id", 0L)
                val dStr = if (rDate > 0) shortDateFormat.format(Date(rDate * 1000)) else ""
                holder.replyMeta.text = if (rId > 0) "ID:$rId $dStr" else dStr
                holder.replyContainer.visibility = View.VISIBLE
            } catch (e: Exception) { holder.replyContainer.visibility = View.GONE }
        } else {
            holder.replyContainer.visibility = View.GONE
        }

        // 媒体解析渲染核心逻辑
        holder.mediaContainer.visibility = View.GONE
        holder.fileContainer.visibility = View.GONE
        holder.mediaJob?.cancel()
        holder.mediaImage.setImageDrawable(null)
        
        val prefs = ctx.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("bot_token", "") ?: ""

        if (rawObj.has("photo") || rawObj.has("sticker") || rawObj.has("video")) {
            holder.mediaContainer.visibility = View.VISIBLE
            var fileId = ""
            var isVideo = false
            var autoCache = false
            
            if (rawObj.has("photo")) {
                val arr = rawObj.getJSONArray("photo")
                fileId = arr.getJSONObject(arr.length() - 1).getString("file_id")
                autoCache = prefs.getBoolean("auto_image", false)
            } else if (rawObj.has("sticker")) {
                fileId = rawObj.getJSONObject("sticker").getString("file_id")
                autoCache = prefs.getBoolean("auto_sticker", false)
            } else if (rawObj.has("video")) {
                val vid = rawObj.getJSONObject("video")
                fileId = vid.optJSONObject("thumbnail")?.optString("file_id") ?: vid.getString("file_id")
                isVideo = true
                autoCache = false 
            }

            holder.mediaOverlayIcon.setImageResource(if (isVideo) android.R.drawable.ic_media_play else android.R.drawable.stat_sys_download)
            holder.mediaOverlay.visibility = if (autoCache) View.GONE else View.VISIBLE

            val loadMedia = {
                holder.mediaOverlay.visibility = View.GONE
                holder.mediaJob = scope.launch {
                    val url = FileHelper.getTelegramFileUrl(fileId, token)
                    if (holder.boundMessageId == currentMsgId && !url.isNullOrEmpty()) {
                        if (isVideo && !rawObj.getJSONObject("video").has("thumbnail")) {
                            // 若是无缩略图视频直接发起隐式播放
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(url), "video/*")
                            }
                            try { ctx.startActivity(intent) } catch (_: Exception) { }
                        } else {
                            val req = ImageRequest.Builder(ctx).data(url).target(holder.mediaImage).crossfade(true).build()
                            Coil.imageLoader(ctx).enqueue(req)
                        }
                    }
                }
            }

            if (autoCache) loadMedia()
            
            holder.mediaOverlay.setOnClickListener { loadMedia() }
            holder.mediaImage.setOnClickListener {
                if (isVideo) {
                    holder.mediaJob = scope.launch {
                        val vUrl = FileHelper.getTelegramFileUrl(rawObj.getJSONObject("video").getString("file_id"), token)
                        if (!vUrl.isNullOrEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(vUrl), "video/*") }
                            try { ctx.startActivity(intent) } catch (_: Exception) {}
                        }
                    }
                }
            }
        } else if (rawObj.has("document")) {
            holder.fileContainer.visibility = View.VISIBLE
            val doc = rawObj.getJSONObject("document")
            val fName = doc.optString("file_name", "未知文件")
            val fSize = doc.optLong("file_size", 0L)
            holder.fileNameText.text = fName
            holder.fileSizeText.text = String.format("%.2f MB", fSize / (1024f * 1024f))
            
            holder.fileContainer.setOnClickListener {
                scope.launch {
                    val url = FileHelper.getTelegramFileUrl(doc.getString("file_id"), token)
                    if (!url.isNullOrEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try { ctx.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            }
        }

        // 富文本渲染
        val rawText = message.text ?: ""
        if (rawText.isEmpty()) {
            holder.messageText.visibility = View.GONE
        } else {
            holder.messageText.visibility = View.VISIBLE
            val formatted = MessageFormatter.format(rawText, message.entities)
            val spannable = if (formatted is Spannable) formatted else SpannableString(formatted ?: "")
            val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
            for (urlSpan in urlSpans) {
                val start = spannable.getSpanStart(urlSpan)
                val end = spannable.getSpanEnd(urlSpan)
                spannable.removeSpan(urlSpan)
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("打开链接")
                            .setMessage(urlSpan.url)
                            .setPositiveButton("打开") { _, _ ->
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlSpan.url)))
                            }
                            .setNegativeButton("取消", null).show()
                    }
                    override fun updateDrawState(ds: TextPaint) { super.updateDrawState(ds); ds.isUnderlineText = true }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            holder.messageText.text = spannable
            holder.messageText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }

        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = message.date * 1000 }
        val dateStr = if (msgCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(message.date * 1000))
        } else {
            SimpleDateFormat("yy年MM月dd日", Locale.getDefault()).format(Date(message.date * 1000))
        }
        holder.messageInfo.text = "ID:${message.messageId}  $dateStr ${timeFormat.format(Date(message.date * 1000))}"

        holder.loadJob?.cancel()
        if (!isOutgoing && prefs.getBoolean("use_real_avatar", true)) {
            val userId = message.senderUserId
            if (userId != null && userId != 0L) {
                holder.loadJob = scope.launch {
                    if (holder.boundMessageId != currentMsgId) return@launch
                    val avatarUrl = AvatarHelper.getUserAvatar(userId)
                    if (!avatarUrl.isNullOrEmpty()) {
                        val req = ImageRequest.Builder(ctx).data(avatarUrl).crossfade(true).transformations(CircleCropTransformation())
                            .listener(onSuccess = { _, res ->
                                if (holder.boundMessageId == currentMsgId) {
                                    holder.avatar.setImageDrawable(res.drawable)
                                    holder.avatarFallback.visibility = View.GONE
                                    holder.avatar.visibility = View.VISIBLE
                                }
                            }).build()
                        Coil.imageLoader(ctx).enqueue(req)
                    }
                }
            }
        }

        holder.itemView.setOnClickListener { onClick?.invoke(message) }
        holder.itemView.setOnLongClickListener { view -> onLongClick?.invoke(message, view) ?: false }
    }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId
        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem == newItem
    }
}
