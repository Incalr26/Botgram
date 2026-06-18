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
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity
import com.incalr26.botgram.util.AvatarHelper
import com.incalr26.botgram.util.MessageFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessageAdapter : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val avatarFallback: TextView = view.findViewById(R.id.avatarFallback)
        val senderName: TextView = view.findViewById(R.id.senderName)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val container: LinearLayout = view as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)
        val isOutgoing = message.isOutgoing

        if (isOutgoing) {
            holder.avatar.visibility = View.GONE
            holder.avatarFallback.visibility = View.GONE
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_RTL
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.outgoing_bg)
        } else {
            // 设置首字母回退
            val senderName = message.senderName ?: "?"
            val fallback = senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatarFallback.text = fallback
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatar.visibility = View.GONE

            val userId = message.senderUserId
            val chatId = message.chatId
            if (userId != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        AvatarHelper.loadInto(holder.avatar, userId, chatId, "private")
                        holder.avatar.post {
                            if (holder.avatar.drawable == null) {
                                holder.avatarFallback.visibility = View.VISIBLE
                                holder.avatar.visibility = View.GONE
                            } else {
                                holder.avatarFallback.visibility = View.GONE
                                holder.avatar.visibility = View.VISIBLE
                            }
                        }
                    } catch (e: Exception) {
                        holder.avatarFallback.visibility = View.VISIBLE
                        holder.avatar.visibility = View.GONE
                    }
                }
            } else {
                holder.avatarFallback.visibility = View.VISIBLE
            }
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.incoming_bg)
        }

        holder.senderName.text = message.senderName ?: "未知"

        val rawText = message.text ?: ""
        val formatted = MessageFormatter.format(rawText, message.entities)
        holder.messageText.text = formatted
    }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem == newItem
    }
}
