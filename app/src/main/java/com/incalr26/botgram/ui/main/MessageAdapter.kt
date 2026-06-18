package com.incalr26.botgram.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.MessageEntity

class MessageAdapter : ListAdapter<MessageEntity, MessageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.avatar)
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
            // 自己发送：头像隐藏，文本靠右
            holder.avatar.visibility = View.GONE
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_RTL
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.outgoing_bg)
        } else {
            holder.avatar.visibility = View.VISIBLE
            holder.avatar.text = (message.senderName ?: "?").take(1).uppercase()
            holder.container.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.messageText.background = holder.itemView.context.getDrawable(R.drawable.incoming_bg)
        }

        holder.senderName.text = message.senderName ?: "未知"
        holder.messageText.text = message.text ?: ""
    }

    class DiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem.messageId == newItem.messageId && oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity) =
            oldItem == newItem
    }
}
