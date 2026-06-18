package com.incalr26.botgram.util

import androidx.lifecycle.MutableLiveData

object NetworkStateHolder {
    val isConnected = MutableLiveData(true) // 初始假设连接正常

    fun updateState(connected: Boolean) {
        isConnected.postValue(connected)
    }
}
