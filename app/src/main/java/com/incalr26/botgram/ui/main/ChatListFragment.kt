package com.incalr26.botgram.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.R
import com.incalr26.botgram.data.repository.ChatRepository

class ChatListFragment : Fragment() {
    private lateinit var chatRepository: ChatRepository
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.chatRecyclerView)
        val emptyHint = view.findViewById<View>(R.id.emptyHint)

        chatRepository = ChatRepository(BotApp.instance.databaseHelper)
        adapter = ChatAdapter { chat ->
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("chatId", chat.chatId)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        chatRepository.allChats.observe(viewLifecycleOwner, Observer { chats ->
            adapter.submitList(chats)
            emptyHint.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
        })
        chatRepository.refreshChats()
    }
}
