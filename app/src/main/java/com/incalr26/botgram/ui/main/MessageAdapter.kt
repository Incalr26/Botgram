package com.incalr26.botgram.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.bumptech.glide.Glide
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val onClick: ((MessageEntity, View) -> Unit)? = null,
    private val onLongClick: ((MessageEntity, View) -> Boolean)? = null,
    private val onAvatarClick: ((MessageEntity) -> Unit)? = null,
    private val onAvatarLongClick: ((MessageEntity) -> Unit)? = null,
    private val onFileClick: ((MessageEntity) -> Unit)? = null,
    private val onReactionToggle: ((MessageEntity, String) -> Unit)? = null,
    private val onPhotoPreview: ((String) -> Unit)? = null
) : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun formatSize(size: Long): String {
        if (size <= 0) return "未知"
        if (size < 1024) return "${size} B"
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024f)
        return String.format("%.2f MB", size / (1024f * 1024f))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateHeader: TextView = view.findViewById(R.id.dateHeader)
        val systemMessageText: TextView = view.findViewById(R.id.systemMessageText)
        val systemReactionsContainer: LinearLayout = view.findViewById(R.id.systemReactionsContainer)
        val mainMessageContainer: LinearLayout = view.findViewById(R.id.mainMessageContainer)
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val avatarFallback: TextView = view.findViewById(R.id.avatarFallback)
        val senderName: TextView = view.findViewById(R.id.senderName)
        val bubbleContainer: MaterialCardView = view.findViewById(R.id.bubbleContainer)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val messageInfo: TextView = view.findViewById(R.id.messageInfo)
        val forwardInfo: TextView = view.findViewById(R.id.forwardInfo)
        val replyContainer: LinearLayout = view.findViewById(R.id.replyContainer)
        val replyName: TextView = view.findViewById(R.id.replyName)
        val replyMeta: TextView = view.findViewById(R.id.replyMeta)
        val replyPreview: TextView = view.findViewById(R.id.replyPreview)
        val mediaContainer: MaterialCardView = view.findViewById(R.id.mediaContainer)
        val mediaImage: ImageView = view.findViewById(R.id.mediaImage)
        val mediaOverlayContainer: FrameLayout = view.findViewById(R.id.mediaOverlayContainer)
        val mediaOverlayText: TextView = view.findViewById(R.id.mediaOverlayText)
        val fileContainer: MaterialCardView = view.findViewById(R.id.fileContainer)
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        val fileSizeText: TextView = view.findViewById(R.id.fileSizeText)
        val inlineKeyboardContainer: LinearLayout = view.findViewById(R.id.inlineKeyboardContainer)
        val reactionsContainer: LinearLayout = view.findViewById(R.id.reactionsContainer)
        
        var boundMessageId: Long = 0L
        var loadJob: Job? = null
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
        val prefs = ctx.getSharedPreferences("botgram_prefs", Context.MODE_PRIVATE)

        val currentDate = Date(message.date * 1000)
        val cal = Calendar.getInstance().apply { time = currentDate }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val dateDf = if (cal.get(Calendar.YEAR) == currentYear) SimpleDateFormat("M月d日", Locale.getDefault()) else SimpleDateFormat("yy年M月d日", Locale.getDefault())
        val dateString = dateDf.format(currentDate)
        
        var showDateHeader = true
        if (position > 0) {
            val prevDateString = dateDf.format(Date(getItem(position - 1).date * 1000))
            if (dateString == prevDateString) showDateHeader = false
        }
        holder.dateHeader.visibility = if (showDateHeader) View.VISIBLE else View.GONE
        if (showDateHeader) holder.dateHeader.text = dateString

        val rawObj = try { JSONObject(message.rawJson ?: "{}") } catch (e: Exception) { JSONObject() }
        
        val renderReactions = { container: LinearLayout, alignCenter: Boolean ->
            container.removeAllViews()
            if (!message.reactions.isNullOrEmpty() && message.reactions != "[]") {
                try {
                    val arr = JSONArray(message.reactions)
                    if (arr.length() > 0) {
                        container.visibility = View.VISIBLE
                        for (i in 0 until arr.length()) {
                            val r = arr.getJSONObject(i)
                            val emojiStr = if (r.has("type") && r.optJSONObject("type")?.optString("type") == "emoji") r.getJSONObject("type").optString("emoji", "❓") else r.optString("emoji", "❓")
                            val count = if (r.has("total_count")) r.getInt("total_count") else r.optInt("count", 1)
                            val isChosen = r.optBoolean("chosen", false)
                            
                            val tv = TextView(ctx).apply {
                                text = "$emojiStr $count"
                                textSize = 12f
                                val bgAttr = if (isChosen) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurfaceVariant
                                val textAttr = if (isChosen) com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSurfaceVariant
                                setTextColor(ctx.getColorAttr(textAttr))
                                background = GradientDrawable().apply { setColor(ctx.getColorAttr(bgAttr)); cornerRadius = 32f; if (isChosen) setStroke(2, ctx.getColorAttr(com.google.android.material.R.attr.colorPrimary)) }
                                setPadding(16, 6, 16, 6)
                                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8; marginStart = if (alignCenter && i == 0) 8 else 0 }
                                setOnClickListener { onReactionToggle?.invoke(message, emojiStr) }
                            }
                            container.addView(tv)
                        }
                    } else container.visibility = View.GONE
                } catch (e: Exception) { container.visibility = View.GONE }
            } else container.visibility = View.GONE
        }

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
                sysTxt = "$doer 邀请了 ${names.joinToString(", ")} 加入"
            } else if (rawObj.has("left_chat_member")) {
                val left = rawObj.getJSONObject("left_chat_member").optString("first_name", "")
                sysTxt = if (doer == left) "$doer 离开了聊天" else "$doer 移除了 $left"
            } else if (rawObj.has("pinned_message")) { sysTxt = "$doer 置顶了消息"
            } else if (rawObj.has("new_chat_title")) { sysTxt = "$doer 修改了名称" }
            holder.systemMessageText.text = sysTxt
            
            holder.systemMessageText.setOnClickListener { onClick?.invoke(message, holder.systemMessageText) }
            renderReactions(holder.systemReactionsContainer, true)
            return
        }

        holder.mainMessageContainer.visibility = View.VISIBLE
        holder.systemMessageText.visibility = View.GONE
        holder.systemReactionsContainer.visibility = View.GONE
        Coil.imageLoader(ctx).enqueue(ImageRequest.Builder(ctx).data(null).target(holder.mediaImage).build())

        val typedValue = TypedValue()
        if (isOutgoing) {
            holder.avatar.visibility = View.GONE
            holder.avatarFallback.visibility = View.GONE
            holder.mainMessageContainer.layoutDirection = View.LAYOUT_DIRECTION_RTL
            ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            holder.bubbleContainer.setCardBackgroundColor(typedValue.data)
            holder.senderName.visibility = View.GONE
        } else {
            holder.mainMessageContainer.layoutDirection = View.LAYOUT_DIRECTION_LTR
            ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
            holder.bubbleContainer.setCardBackgroundColor(typedValue.data)
            holder.senderName.visibility = View.VISIBLE
            
            val fallback = message.senderName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatarFallback.text = fallback
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatar.visibility = View.GONE
            
            val clickAvatar = View.OnClickListener { onAvatarClick?.invoke(message) }
            val longClickAvatar = View.OnLongClickListener { onAvatarLongClick?.invoke(message); true }
            holder.avatar.setOnClickListener(clickAvatar)
            holder.avatar.setOnLongClickListener(longClickAvatar)
            holder.avatarFallback.setOnClickListener(clickAvatar)
            holder.avatarFallback.setOnLongClickListener(longClickAvatar)
        }

        // 精准的管理员标签逻辑
        val fromObj = rawObj.optJSONObject("from")
        val fNameStr = fromObj?.optString("first_name", "") ?: ""
        val lNameStr = fromObj?.optString("last_name", "") ?: ""
        var combinedName = listOf(fNameStr, lNameStr).filter { it.isNotEmpty() }.joinToString(" ")
        if (combinedName.isEmpty()) combinedName = message.senderName ?: "未知"

        val nameBuilder = SpannableStringBuilder(combinedName)
        val role = message.senderRole ?: ""
        var adminBase = ""
        if (role == "creator" || role == "owner") adminBase = "所有者"
        else if (role == "administrator") adminBase = "管理员"

        if (adminBase.isNotEmpty()) {
            val customTitle = rawObj.optString("author_signature", message.senderTitle ?: "")
            val finalTag = if (customTitle.isNotEmpty()) " [$adminBase $customTitle]" else " [$adminBase]"
            val start = nameBuilder.length
            nameBuilder.append(finalTag)
            nameBuilder.setSpan(ForegroundColorSpan(Color.parseColor("#4CAF50")), start, nameBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        holder.senderName.text = nameBuilder

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
        
        val fileCachePrefs = ctx.getSharedPreferences("botgram_file_cache", Context.MODE_PRIVATE)
        val token = prefs.getString("bot_token", "") ?: ""
        var actualText = message.text ?: ""; var mediaLabel = ""

        if (rawObj.has("photo") || rawObj.has("sticker") || rawObj.has("video")) {
            holder.mediaContainer.visibility = View.VISIBLE
            var fileSize = 0L; var autoCache = false; var isVideo = false
            if (rawObj.has("photo")) {
                actualText = rawObj.optString("caption", actualText); mediaLabel = "[图片] "
                val arr = rawObj.getJSONArray("photo"); fileSize = arr.getJSONObject(arr.length() - 1).optLong("file_size", 0L)
            } else if (rawObj.has("sticker")) {
                actualText = ""
                val emoji = rawObj.getJSONObject("sticker").optString("emoji", "")
                mediaLabel = if (emoji.isNotEmpty()) "[贴纸 $emoji] " else "[贴纸] "
                fileSize = rawObj.getJSONObject("sticker").optLong("file_size", 0L)
            } else if (rawObj.has("video")) {
                actualText = rawObj.optString("caption", actualText); mediaLabel = "[视频] "
                fileSize = rawObj.getJSONObject("video").optLong("file_size", 0L); isVideo = true
            }

            val savedPath = fileCachePrefs.getString(currentMsgId.toString(), null)
            if (savedPath != null && File(savedPath).exists()) {
                holder.mediaOverlayContainer.visibility = View.GONE
                Glide.with(ctx).load(File(savedPath)).into(holder.mediaImage)
            } else {
                holder.mediaImage.setImageDrawable(null)
                holder.mediaOverlayText.text = formatSize(fileSize)
                holder.mediaOverlayContainer.visibility = View.VISIBLE
            }

            holder.mediaOverlayContainer.setOnClickListener { onFileClick?.invoke(message) }
            holder.mediaImage.setOnClickListener {
                val currentPath = fileCachePrefs.getString(currentMsgId.toString(), null)
                if (currentPath != null && File(currentPath).exists()) {
                    if (isVideo) {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file://$currentPath"), "video/*"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }) } catch (_: Exception) {}
                    } else onPhotoPreview?.invoke(currentPath)
                } else onFileClick?.invoke(message)
            }
            holder.mediaImage.setOnLongClickListener { onLongClick?.invoke(message, holder.bubbleContainer) ?: false }
        } else if (rawObj.has("document") || rawObj.has("audio") || rawObj.has("voice")) {
            val isAudio = rawObj.has("audio") || rawObj.has("voice")
            actualText = rawObj.optString("caption", actualText); mediaLabel = if (isAudio) "[音频] " else "[文件] "
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
            
            val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
            for (urlSpan in urlSpans) {
                val start = builder.getSpanStart(urlSpan); val end = builder.getSpanEnd(urlSpan); val flags = builder.getSpanFlags(urlSpan)
                builder.removeSpan(urlSpan)
                builder.setSpan(object : ClickableSpan() {
                    override fun onClick(w: View) { 
                        MaterialAlertDialogBuilder(ctx).setTitle("安全提示").setMessage("即将访问链接：\n\n${urlSpan.url}").setPositiveButton("继续访问") { _, _ -> try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlSpan.url))) } catch(e:Exception){} }.setNegativeButton("取消", null).show() 
                    }
                    override fun updateDrawState(ds: TextPaint) { 
                        super.updateDrawState(ds)
                        val tv = TypedValue(); ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                        ds.color = tv.data
                        ds.isUnderlineText = true 
                    }
                }, start, end, flags)
            }
            holder.messageText.text = builder
            
            holder.messageText.movementMethod = object : LinkMovementMethod() {
                override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
                    val action = event.action
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                        var x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                        var y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                        val layout = widget.layout
                        val line = layout.getLineForVertical(y)
                        val off = layout.getOffsetForHorizontal(line, x.toFloat())
                        val links = buffer.getSpans(off, off, ClickableSpan::class.java)
                        if (links.isNotEmpty()) {
                            if (action == MotionEvent.ACTION_UP) links[0].onClick(widget)
                            return true
                        }
                    }
                    return super.onTouchEvent(widget, buffer, event)
                }
            }
            holder.messageText.setOnClickListener { onClick?.invoke(message, holder.bubbleContainer) }
        }

        holder.inlineKeyboardContainer.removeAllViews()
        if (rawObj.has("reply_markup")) {
            val markup = rawObj.getJSONObject("reply_markup")
            if (markup.has("inline_keyboard")) {
                holder.inlineKeyboardContainer.visibility = View.VISIBLE
                val keyboard = markup.getJSONArray("inline_keyboard")
                for (i in 0 until keyboard.length()) {
                    val rowArray = keyboard.getJSONArray(i)
                    val rowLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
                    for (j in 0 until rowArray.length()) {
                        val btnObj = rowArray.getJSONObject(j)
                        val btn = Button(ctx).apply { 
                            text = btnObj.optString("text", "Button")
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 4, 4, 4) }
                            setOnClickListener { Toast.makeText(ctx, "Botgram API 交互施工中...", Toast.LENGTH_SHORT).show() }
                        }
                        rowLayout.addView(btn)
                    }
                    holder.inlineKeyboardContainer.addView(rowLayout)
                }
            } else holder.inlineKeyboardContainer.visibility = View.GONE
        } else holder.inlineKeyboardContainer.visibility = View.GONE

        renderReactions(holder.reactionsContainer, false)

        val timeDf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeString = timeDf.format(currentDate)
        val editStr = if (message.isEdited) " [已编辑]" else ""
        holder.messageInfo.text = "$mediaLabel ID:${message.messageId}  $timeString$editStr"

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
        
        holder.bubbleContainer.setOnClickListener { onClick?.invoke(message, holder.bubbleContainer) }
        holder.mainMessageContainer.setOnClickListener { onClick?.invoke(message, holder.bubbleContainer) }
    }
    
    private fun Context.getColorAttr(attr: Int): Int { val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId
        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) = oldItem == newItem
    }
}
