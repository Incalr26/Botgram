package com.incalr26.botgram.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.incalr26.botgram.databinding.ActivityLoginBinding
import com.incalr26.botgram.data.remote.ApiClient
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 已保存 token 则直接进入主界面
        val token = getSharedPreferences("botgram_prefs", MODE_PRIVATE)
            .getString("bot_token", null)
        if (!token.isNullOrBlank()) {
            startMainAndFinish()
            return
        }

        binding.loginButton.setOnClickListener {
            val inputToken = binding.tokenInput.text.toString().trim()
            if (inputToken.isEmpty()) {
                Toast.makeText(this, "请输入 Token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            login(inputToken)
        }
    }

    private fun login(token: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.telegram.org/bot$token/getMe"
                val request = Request.Builder().url(url).build()
                val response = ApiClient.getClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "")
                    if (json.getBoolean("ok")) {
                        getSharedPreferences("botgram_prefs", MODE_PRIVATE)
                            .edit().putString("bot_token", token).apply()
                        withContext(Dispatchers.Main) {
                            startMainAndFinish()
                        }
                    } else {
                        showError("Bot 无效")
                    }
                } else {
                    showError("网络请求失败")
                }
            } catch (e: Exception) {
                showError("网络错误: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun startMainAndFinish() {
        // 仅跳转主界面，服务启动交予 MainActivity（确保在前台）
        startActivity(Intent(this, com.incalr26.botgram.ui.main.MainActivity::class.java))
        finish()
    }

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}
