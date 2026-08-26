package com.chobgroup.rootnet.vpn

import androidx.compose.runtime.mutableStateOf

/** Connection state shared between the engine service and the UI. */
object EngineState {

    enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, QUOTA_EXHAUSTED, ERROR }

    private val _state = mutableStateOf(ConnState.DISCONNECTED)
    val state get() = _state

    private val _message = mutableStateOf<String?>(null)
    val message get() = _message

    fun set(state: ConnState, message: String? = null) {
        _state.value = state
        if (message != null || state != ConnState.ERROR) _message.value = message
    }
}
