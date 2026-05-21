package com.example.util

import com.example.data.model.SmsMsg
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsArrivalEventBus {
    private val _incomingSmsFlow = MutableSharedFlow<SmsMsg>(extraBufferCapacity = 10)
    val incomingSmsFlow: SharedFlow<SmsMsg> = _incomingSmsFlow.asSharedFlow()

    fun postNewSms(sms: SmsMsg) {
        _incomingSmsFlow.tryEmit(sms)
    }
}
