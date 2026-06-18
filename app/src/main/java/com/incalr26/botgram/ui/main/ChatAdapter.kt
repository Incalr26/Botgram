package com.incalr26.botgram.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.ChatEntity

class ChatAdapter(private val onClick: (ChatEntity) -> Unit) :
    ListAdapter<ChatEntity, ChatAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarText: TextView = view.findViewById(R.id.avatarText)
        val chatName: TextView = view.findViewById(R.id.chatName)
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
        // 头像显示首字母，若名为空则显示"?"
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.avatarText.text = initial

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
