package com.incalr26.botgram

import android.app.Application
import android.content.Intent
import com.incalr26.botgram.data.local.DatabaseHelper
import com.incalr26.botgram.data.remote.ApiClient
import com.incalr26.botgram.ui.main.CrashActivity
import com.incalr26.botgram.util.LogManager
import java.io.PrintWriter
import java.io.StringWriter

class BotApp : Application() {
    lateinit var databaseHelper: DatabaseHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 设置全局未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            LogManager.write(this, "UNCAUGHT: $stackTrace")
            
            val intent = Intent(this, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("stack_trace", stackTrace)
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        databaseHelper = DatabaseHelper(this)
        ApiClient.init(this)
    }

    companion object {
        lateinit var instance: BotApp
            private set
    }
}
