package com.example.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.model.Track
import com.example.musicplayer.player.PlaybackService
import com.example.musicplayer.player.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex = _currentIndex.asStateFlow()

    private val _lyrics = MutableStateFlow("")
    val lyrics = _lyrics.asStateFlow()

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
            val track = mediaItem?.mediaId?.let { trackById[it] }
            _currentTrack.value = track
            controller?.let { _currentIndex.value = it.currentMediaItemIndex }
            track?.let { loadMetadata(it) }
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
        context.startForegroundService(Intent(context, PlaybackService::class.java))
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(listener)
            _isConnected.value = true
            controller?.currentMediaItem?.mediaId?.let {
                _currentTrack.value = trackById[it]
                trackById[it]?.let { t -> loadMetadata(t) }
            }
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
            _queue.value = tracks
            _currentIndex.value = startIndex
        }
    }

    fun playQueueIndex(index: Int) {
        if (index in 0 until (_queue.value.size)) {
            controller?.seekTo(index, 0L)
            _currentIndex.value = index
        }
    }

    fun togglePlay() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun next() {
        controller?.let { c ->
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
            } else {
                c.seekToDefaultPosition(0)
                c.play()
            }
        }
    }
    fun prev() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    /**
     * 播放模式循环：顺序播放 → 列表循环 → 单曲循环 → 随机播放 → 顺序播放
     */
    fun cyclePlayMode() = controller?.let { c ->
        when {
            c.shuffleModeEnabled -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_OFF
            }
            c.repeatMode == Player.REPEAT_MODE_OFF -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
            }
            c.repeatMode == Player.REPEAT_MODE_ALL -> {
                c.repeatMode = Player.REPEAT_MODE_ONE
            }
            else -> {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.shuffleModeEnabled = true
            }
        }
    }

    /**
     * 提取歌词和内嵌封面：查找同名 .lrc 文件；
     * 如果文件夹歌曲没有封面，尝试从文件中提取内嵌封面缓存。
     */
    private fun loadMetadata(track: Track) {
        _lyrics.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var lyrics = ""
            var embeddedArt: ByteArray? = null

            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, track.uri)
                if (track.albumArtUri == null) {
                    embeddedArt = retriever.embeddedPicture
                }
                retriever.release()
            }

            lyrics = findLrcSidecar(context, track) ?: ""
            lyrics = stripLrcTimestamps(lyrics)

            var artUri: Uri? = track.albumArtUri
            if (embeddedArt != null && track.albumArtUri == null) {
                runCatching {
                    val cacheFile = File(context.cacheDir, "art_${track.id}.jpg")
                    cacheFile.writeBytes(embeddedArt!!)
                    artUri = Uri.fromFile(cacheFile)
                }
            }

            withContext(Dispatchers.Main) {
                _lyrics.value = lyrics
                if (artUri != track.albumArtUri) {
                    _currentTrack.value = track.copy(albumArtUri = artUri)
                }
            }
        }
    }

    /** 查找与音频文件同目录、同名的 .lrc 文件。 */
    private fun findLrcSidecar(context: Context, track: Track): String? {
        // 文件夹扫描的歌曲：通过 DocumentFile 找同目录同名 .lrc
        if (track.id.startsWith("folder_")) {
            return runCatching {
                val audioDoc = DocumentFile.fromSingleUri(context, track.uri) ?: return null
                val parent = audioDoc.parentFile ?: return null
                val audioName = audioDoc.name ?: return null
                val baseName = audioName.substringBeforeLast('.')
                parent.listFiles().forEach { child ->
                    if (child.isFile) {
                        val lrcName = child.name ?: return@forEach
                        if (lrcName.equals("$baseName.lrc", ignoreCase = true)) {
                            context.contentResolver.openInputStream(child.uri)?.use { input ->
                                return String(input.readBytes())
                            }
                        }
                    }
                }
                null
            }.getOrNull()
        }
        // MediaStore 歌曲：查询 DATA 列拿到文件路径
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(track.id),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val path = c.getString(0) ?: return null
                    val lrcFile = File(path.substringBeforeLast('.') + ".lrc")
                    if (lrcFile.exists()) return lrcFile.readText()
                }
            }
        }
        return null
    }

    /** 去掉 [mm:ss.xx] 时间标签，只保留歌词文本行。 */
    private fun stripLrcTimestamps(text: String): String {
        if (text.isBlank()) return ""
        val regex = Regex("""\[\d{2}:\d{2}[.:]\d{2,3}\]""")
        return text.lines()
            .map { regex.replace(it, "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    override fun onCleared() {
        positionHandler.removeCallbacks(positionRunnable)
        controller?.removeListener(listener)
        super.onCleared()
    }
}