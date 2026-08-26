package com.chobgroup.rootnet.vpn

import androidx.compose.runtime.mutableStateOf

/** Connection state shared between the engine service and the UI. */
object EngineState {

    enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, QUOTA_EXHAUSTED, ERROR }

    private val _state = mutableStateOf(ConnState.DISCONNECTED)
    val state get() = _state

    private val _message = mutableStateOf<String?>(null)
    val message get() = _message

    /** rawConfig of the config currently connecting/connected; null otherwise. */
    private val _activeConfig = mutableStateOf<String?>(null)
    val activeConfig get() = _activeConfig

    fun set(state: ConnState, message: String? = null) {
        _state.value = state
        if (message != null || state != ConnState.ERROR) _message.value = message
    }

    fun setActiveConfig(rawConfig: String?) {
        _activeConfig.value = rawConfig
    }
}
