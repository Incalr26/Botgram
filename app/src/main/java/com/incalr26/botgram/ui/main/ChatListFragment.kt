package com.incalr26.botgram.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.R
import com.incalr26.botgram.data.repository.ChatRepository
import com.incalr26.botgram.util.NewMessageNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatListFragment : Fragment() {
    private lateinit var chatRepository: ChatRepository
    private lateinit var adapter: ChatAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

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
        swipeRefresh = view.findViewById(R.id.swipeRefresh)

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
            swipeRefresh.isRefreshing = false
        })

        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch(Dispatchers.IO) {
                chatRepository.refreshChats()
            }
        }

        // 观察全局新消息
        NewMessageNotifier.newMessage.observe(viewLifecycleOwner) {
            lifecycleScope.launch(Dispatchers.IO) {
                chatRepository.refreshChats()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.refreshChats()
        }
    }
}
