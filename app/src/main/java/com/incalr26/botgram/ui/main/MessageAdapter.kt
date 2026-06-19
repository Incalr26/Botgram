package com.incalr26.botgram.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.util.AvatarHelper
import com.incalr26.botgram.util.MessageFormatter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val avatarFallback: TextView = view.findViewById(R.id.avatarFallback)
        val senderName: TextView = view.findViewById(R.id.senderName)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val messageInfo: TextView = view.findViewById(R.id.messageInfo)
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

        // 同步设置方向、背景和文本
        if (isOutgoing) {
            holder.avatar.visibility = View.GONE
            holder.avatarFallback.visibility = View.GONE
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_RTL
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.outgoing_bg)
        } else {
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.incoming_bg)
            // 先显示首字母
            val senderName = message.senderName ?: "?"
            val fallback = senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatarFallback.text = fallback
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatar.visibility = View.GONE
        }

        holder.senderName.text = message.senderName ?: "未知"
        val rawText = message.text ?: ""
        holder.messageText.text = MessageFormatter.format(rawText, message.entities)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date(message.date * 1000))
        holder.messageInfo.text = "ID:${message.messageId}  $timeStr"

        // 异步加载头像
        if (!isOutgoing) {
            val userId = message.senderUserId
            if (userId != null) {
                holder.loadJob?.cancel()
                holder.loadJob = CoroutineScope(Dispatchers.Main).launch {
                    val avatarUrl = AvatarHelper.getUserProfilePhotos(userId)
                    if (holder.boundMessageId != currentMsgId) return@launch

                    if (avatarUrl != null && avatarUrl != "none") {
                        val request = ImageRequest.Builder(holder.itemView.context)
                            .data(avatarUrl)
                            .crossfade(true)
                            .transformations(CircleCropTransformation())
                            .target(holder.avatar)
                            .listener(
                                onSuccess = { _, _ ->
                                    holder.avatarFallback.visibility = View.GONE
                                    holder.avatar.visibility = View.VISIBLE
                                },
                                onError = { _, _ ->
                                    holder.avatarFallback.visibility = View.VISIBLE
                                    holder.avatar.visibility = View.GONE
                                }
                            )
                            .build()
                        holder.itemView.context.imageLoader.enqueue(request)
                    } else {
                        // 无头像或网络错误，保持首字母
                        holder.avatarFallback.visibility = View.VISIBLE
                        holder.avatar.visibility = View.GONE
                    }
                }
            }
        } else {
            holder.loadJob?.cancel()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem == newItem
    }
}
