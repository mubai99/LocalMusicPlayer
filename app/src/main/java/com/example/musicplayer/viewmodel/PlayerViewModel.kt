package com.example.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.model.Track
import com.example.musicplayer.player.PlaybackService
import com.example.musicplayer.player.toMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 负责与播放服务（MediaController）交互：播放列表、播放/暂停、进度、上一首/下一首、随机、循环。
 * UI 只和本 ViewModel 打交道，播放服务独立在后台运行。
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private var controller: MediaController? = null
    private val trackById = mutableMapOf<String, Track>()

    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = object : Runnable {
        override fun run() {
            controller?.let {
                _positionMs.value = it.currentPosition.coerceAtLeast(0)
                if (it.duration > 0) _durationMs.value = it.duration
            }
            positionHandler.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            controller?.let { if (it.duration > 0) _durationMs.value = it.duration }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentTrack.value = mediaItem?.mediaId?.let { trackById[it] }
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffle.value = enabled
        }

        override fun onRepeatModeChanged(mode: Int) {
            _repeatMode.value = mode
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val context: Context = getApplication()
        // 确保播放服务已启动，MediaController 才能连上会话
        context.startService(Intent(context, PlaybackService::class.java))
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(listener)
            _isConnected.value = true
            controller?.currentMediaItem?.mediaId?.let { _currentTrack.value = trackById[it] }
            _isPlaying.value = controller?.isPlaying ?: false
            positionHandler.post(positionRunnable)
        }, ContextCompat.getMainExecutor(context))
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        controller?.let { c ->
            trackById.clear()
            c.setMediaItems(tracks.map { t -> t.toMediaItem().also { trackById[t.id] = t } })
            c.seekToDefaultPosition(startIndex)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun next() = controller?.seekToNextMediaItem()
    fun prev() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun onCleared() {
        positionHandler.removeCallbacks(positionRunnable)
        controller?.removeListener(listener)
        // 注意：不释放 controller，后台播放服务仍继续运行
        super.onCleared()
    }
}
