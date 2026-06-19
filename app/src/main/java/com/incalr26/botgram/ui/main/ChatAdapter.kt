package com.incalr26.botgram.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.ChatEntity
import com.incalr26.botgram.util.AvatarHelper
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val onClick: (ChatEntity) -> Unit) :
    ListAdapter<ChatEntity, ChatAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarImage: ImageView = view.findViewById(R.id.avatarImage)
        val avatarFallback: TextView = view.findViewById(R.id.avatarFallback)
        val chatName: TextView = view.findViewById(R.id.chatName)
        val chatTypeLabel: TextView = view.findViewById(R.id.chatTypeLabel)
        val lastMessage: TextView = view.findViewById(R.id.lastMessage)
        val lastTime: TextView = view.findViewById(R.id.lastTime)
        val unreadBadge: TextView = view.findViewById(R.id.unreadBadge)
        var boundChatId: Long = 0L
        var loadJob: Job? = null
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

        val fallback = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.avatarFallback.text = fallback
        holder.avatarFallback.visibility = View.VISIBLE
        holder.avatarImage.visibility = View.GONE
        holder.avatarImage.setImageDrawable(null)

        val currentChatId = chat.chatId
        holder.boundChatId = currentChatId

        // 检查设置是否开启真实头像
        val prefs = holder.itemView.context.getSharedPreferences("botgram_prefs", android.content.Context.MODE_PRIVATE)
        val useRealAvatar = prefs.getBoolean("use_real_avatar", true)

        if (useRealAvatar) {
            holder.loadJob?.cancel()
            holder.loadJob = CoroutineScope(Dispatchers.Main).launch {
                if (holder.boundChatId != currentChatId) return@launch

                val avatarUrl = when (chat.type) {
                    "private" -> AvatarHelper.getUserAvatar(chat.chatId)
                    else -> AvatarHelper.getChatAvatar(chat.chatId)
                }

                if (!avatarUrl.isNullOrEmpty()) {
                    val request = ImageRequest.Builder(holder.itemView.context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .transformations(CircleCropTransformation())
                        .listener(
                            onSuccess = { _, result ->
                                if (holder.boundChatId == currentChatId) {
                                    holder.avatarImage.setImageDrawable(result.drawable)
                                    holder.avatarFallback.visibility = View.GONE
                                    holder.avatarImage.visibility = View.VISIBLE
                                }
                            },
                            onError = { _, _ ->
                                // 保持首字母
                            }
                        )
                        .build()
                    Coil.imageLoader(holder.itemView.context).enqueue(request)
                }
            }
        }

        holder.lastMessage.text = chat.lastMessage ?: ""
        if (chat.lastTime > 0) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.lastTime.text = sdf.format(Date(chat.lastTime))
        } else {
            holder.lastTime.text = ""
        }

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
