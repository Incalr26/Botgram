package com.incalr26.botgram.util

import androidx.lifecycle.MutableLiveData

object NewMessageNotifier {
    val newMessage = MutableLiveData<Unit>()
}
