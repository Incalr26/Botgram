package com.incalr26.botgram.ui.main

import android.content.Intent
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.util.AvatarHelper
import com.incalr26.botgram.util.MessageFormatter
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val onClick: ((MessageEntity) -> Unit)? = null,
    private val onLongClick: ((MessageEntity, View) -> Boolean)? = null
) : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val avatarFallback: TextView = view.findViewById(R.id.avatarFallback)
        val senderName: TextView = view.findViewById(R.id.senderName)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val messageInfo: TextView = view.findViewById(R.id.messageInfo)
        val replyContainer: LinearLayout = view.findViewById(R.id.replyContainer)
        val replyPreview: TextView = view.findViewById(R.id.replyPreview)
        val container: LinearLayout = view as LinearLayout
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

        if (isOutgoing) {
            holder.avatar.visibility = View.GONE
            holder.avatarFallback.visibility = View.GONE
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_RTL
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.outgoing_bg)
        } else {
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.incoming_bg)
            val senderName = message.senderName ?: "?"
            val fallback = senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatarFallback.text = fallback
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatar.visibility = View.GONE
            holder.avatar.setImageDrawable(null)
        }

        // 构建发送者名称（身份 + 标签）
        val nameParts = mutableListOf<String>()
        message.senderName?.let { nameParts.add(it) }

        val role = message.senderRole ?: ""
        val title = message.senderTitle

        // 身份显示
        when (role) {
            "creator" -> nameParts.add("[群主]")
            "administrator" -> nameParts.add("[管理员]")
        }

        // 标签处理
        if (!title.isNullOrEmpty()) {
            if (role == "creator" || role == "administrator") {
                // 合并标签到身份括号内
                val last = nameParts.lastOrNull()
                if (last != null && (last == "[群主]" || last == "[管理员]")) {
                    nameParts[nameParts.lastIndex] = last.removeSuffix("]") + " $title]"
                }
            } else {
                // 普通成员单独添加标签
                nameParts.add("[$title]")
            }
        }

        holder.senderName.text = nameParts.joinToString(" ")

        // 引用预览
        if (!message.replyToJson.isNullOrEmpty()) {
            try {
                val replyMsg = JSONObject(message.replyToJson)
                val replyText = replyMsg.optString("text", null) ?: "[媒体消息]"
                val replySender = replyMsg.optJSONObject("from")?.optString("first_name") ?: "未知"
                holder.replyContainer.visibility = View.VISIBLE
                holder.replyPreview.text = "$replySender: $replyText"
            } catch (e: Exception) {
                holder.replyContainer.visibility = View.GONE
            }
        } else {
            holder.replyContainer.visibility = View.GONE
        }

        // 消息文本格式化
        val rawText = message.text ?: ""
        val formatted = MessageFormatter.format(rawText, message.entities)
        val spannable = formatted as Spannable
        val urlSpans = spannable.getSpans(0, formatted.length, URLSpan::class.java)
        for (urlSpan in urlSpans) {
            val start = spannable.getSpanStart(urlSpan)
            val end = spannable.getSpanEnd(urlSpan)
            spannable.removeSpan(urlSpan)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val url = urlSpan.url
                    MaterialAlertDialogBuilder(holder.itemView.context)
                        .setTitle("打开链接")
                        .setMessage("是否打开以下链接？\n\n$url")
                        .setPositiveButton("打开") { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            holder.itemView.context.startActivity(intent)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        holder.messageText.text = spannable
        holder.messageText.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        // 日期格式
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = message.date * 1000 }
        val year = msgCal.get(Calendar.YEAR)
        val currentYear = now.get(Calendar.YEAR)
        val dateFormat = if (year == currentYear) {
            SimpleDateFormat("MM月dd日", Locale.getDefault())
        } else {
            SimpleDateFormat("yy年MM月dd日", Locale.getDefault())
        }
        val dateStr = dateFormat.format(Date(message.date * 1000))
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = timeFormat.format(Date(message.date * 1000))

        holder.messageInfo.text = "ID:${message.messageId}  $dateStr $timeStr"

        // 头像加载
        val prefs = holder.itemView.context.getSharedPreferences("botgram_prefs", android.content.Context.MODE_PRIVATE)
        val useRealAvatar = prefs.getBoolean("use_real_avatar", true)
        if (!isOutgoing && useRealAvatar) {
            val userId = message.senderUserId
            if (userId != null) {
                holder.loadJob?.cancel()
                holder.loadJob = CoroutineScope(Dispatchers.Main).launch {
                    if (holder.boundMessageId != currentMsgId) return@launch
                    val avatarUrl = AvatarHelper.getUserAvatar(userId)
                    if (!avatarUrl.isNullOrEmpty()) {
                        val request = ImageRequest.Builder(holder.itemView.context)
                            .data(avatarUrl)
                            .crossfade(true)
                            .transformations(CircleCropTransformation())
                            .listener(
                                onSuccess = { _, result ->
                                    if (holder.boundMessageId == currentMsgId) {
                                        holder.avatar.setImageDrawable(result.drawable)
                                        holder.avatarFallback.visibility = View.GONE
                                        holder.avatar.visibility = View.VISIBLE
                                    }
                                },
                                onError = { _, _ -> }
                            )
                            .build()
                        Coil.imageLoader(holder.itemView.context).enqueue(request)
                    }
                }
            }
        } else {
            holder.loadJob?.cancel()
        }

        holder.itemView.setOnClickListener { onClick?.invoke(message) }
        holder.itemView.setOnLongClickListener { view ->
            onLongClick?.invoke(message, view) ?: false
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem == newItem
    }
}
