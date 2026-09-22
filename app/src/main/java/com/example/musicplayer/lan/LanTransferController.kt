package com.example.musicplayer.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 局域网传歌的全局状态中枢（UI 与服务共享）。
 * 服务写入状态，UI 读取；收到文件后回调 onFileReceived 触发本地曲库刷新。
 */
object LanTransferController {
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _endpoint = MutableStateFlow<String?>(null)   // http://ip:port?token=xxx
    val endpoint = _endpoint.asStateFlow()

    private val _token = MutableStateFlow<String?>(null)
    val token = _token.asStateFlow()

    private val _receivedCount = MutableStateFlow(0)
    val receivedCount = _receivedCount.asStateFlow()

    private val _lastReceived = MutableStateFlow<String?>(null)
    val lastReceived = _lastReceived.asStateFlow()

    /** UI 层设置：每成功收到一个文件后调用，用于刷新本地曲库。 */
    var onFileReceived: ((String) -> Unit)? = null

    fun notifyReceived(name: String) {
        _receivedCount.value += 1
        _lastReceived.value = name
        onFileReceived?.invoke(name)
    }

    fun publishRunning(endpoint: String, token: String) {
        _isRunning.value = true
        _endpoint.value = endpoint
        _token.value = token
        _receivedCount.value = 0
        _lastReceived.value = null
    }

    fun publishStopped() {
        _isRunning.value = false
        _endpoint.value = null
        _token.value = null
        _receivedCount.value = 0
        _lastReceived.value = null
    }
}
