package com.incalr26.botgram.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.incalr26.botgram.BotApp
import com.incalr26.botgram.R
import com.incalr26.botgram.data.local.entity.ChatEntity
import com.incalr26.botgram.data.remote.ApiClient
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject

class AddChatDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_chat, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = view.findViewById<EditText>(R.id.chatIdInput)
        view.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            val chatIdStr = input.text.toString().trim()
            if (chatIdStr.isEmpty()) {
                Toast.makeText(requireContext(), "请输入 Chat ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val chatId = chatIdStr.toLongOrNull()
            if (chatId == null) {
                Toast.makeText(requireContext(), "无效的 Chat ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyAndAddChat(chatId)
        }
    }

    private fun verifyAndAddChat(chatId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = requireActivity().getSharedPreferences("botgram_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("bot_token", "") ?: throw Exception("未登录")
                val url = "https://api.telegram.org/bot$token/getChat?chat_id=$chatId"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getBoolean("ok")) {
                        val chatObj = json.getJSONObject("result")
                        val type = chatObj.getString("type")
                        val title = chatObj.optString("title", null)
                        val firstName = chatObj.optString("first_name", null)
                        val lastName = chatObj.optString("last_name", null)
                        val username = chatObj.optString("username", null)
                        val chatEntity = ChatEntity(
                            chatId = chatId,
                            type = type,
                            title = title,
                            firstName = firstName,
                            lastName = lastName,
                            username = username,
                            lastMessage = null,
                            lastTime = System.currentTimeMillis()
                        )
                        val repo = com.incalr26.botgram.data.repository.ChatRepository(BotApp.instance.databaseHelper)
                        repo.insertOrUpdateChat(chatEntity)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "获取失败，请检查 Chat ID 或 Bot 是否在群中", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
