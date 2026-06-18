package com.incalr26.botgram.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.ChatEntity
import com.incalr26.botgram.util.AvatarHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatAdapter(private val onClick: (ChatEntity) -> Unit) :
    ListAdapter<ChatEntity, ChatAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarImage: ImageView = view.findViewById(R.id.avatarImage)
        val avatarFallback: TextView = view.findViewById(R.id.avatarFallback)
        val chatName: TextView = view.findViewById(R.id.chatName)
        val chatTypeLabel: TextView = view.findViewById(R.id.chatTypeLabel)
        val lastMessage: TextView = view.findViewById(R.id.lastMessage)
        val unreadBadge: TextView = view.findViewById(R.id.unreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = getItem(position)
        holder.itemView.setOnClickListener { onClick(chat) }

        val name = if (chat.type == "private") {
            chat.firstName ?: (chat.username ?: "未知")
        } else {
            chat.title ?: "未命名群组"
        }
        holder.chatName.text = name
        holder.chatTypeLabel.text = when (chat.type) {
            "private" -> "私聊"
            "group" -> "群组"
            "supergroup" -> "超级群组"
            "channel" -> "频道"
            else -> chat.type
        }

        // 设置首字母回退
        val fallback = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.avatarFallback.text = fallback
        holder.avatarFallback.visibility = View.VISIBLE
        holder.avatarImage.visibility = View.GONE

        val userId: Long? = if (chat.type == "private") chat.chatId else null
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 加载成功会覆盖，失败保持 fallback 可见
                AvatarHelper.loadInto(holder.avatarImage, userId, chat.chatId, chat.type)
                // 如果 loadInto 完成后图片仍为 null（无网络等），可手动显示 fallback
                holder.avatarImage.post {
                    if (holder.avatarImage.drawable == null) {
                        holder.avatarFallback.visibility = View.VISIBLE
                        holder.avatarImage.visibility = View.GONE
                    } else {
                        holder.avatarFallback.visibility = View.GONE
                        holder.avatarImage.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                holder.avatarFallback.visibility = View.VISIBLE
                holder.avatarImage.visibility = View.GONE
            }
        }

        holder.lastMessage.text = chat.lastMessage ?: ""

        if (chat.unreadCount > 0) {
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = chat.unreadCount.toString()
        } else {
            holder.unreadBadge.visibility = View.GONE
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatEntity>() {
        override fun areItemsTheSame(oldItem: ChatEntity, newItem: ChatEntity) =
            oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: ChatEntity, newItem: ChatEntity) =
            oldItem == newItem
    }
}
