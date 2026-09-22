package com.example.musicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity
import com.example.musicplayer.R
import com.example.musicplayer.data.db.AppDatabase
import com.example.musicplayer.data.db.FavoriteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 基于 Media3 的播放服务：
 * - 使用 ExoPlayer 本地解码，不联网；
 * - 自动向系统注册 MediaSession，提供锁屏/通知/耳机线控；
 * - 前台服务类型 mediaPlayback，息屏可继续播放。
 */
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.example.musicplayer.TOGGLE_FAVORITE"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "music_playback"
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_FAVORITE -> toggleFavorite()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, IntentFilter(ACTION_TOGGLE_FAVORITE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(actionReceiver, IntentFilter(ACTION_TOGGLE_FAVORITE))
        }

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildContentIntent())
            .setMediaNotificationProvider(
                DefaultMediaNotificationProvider.Builder(this)
                    .setNotificationId(NOTIFICATION_ID)
                    .setChannelId(CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .build(),
            )
            .build()
    }

    private fun toggleFavorite() {
        val mediaId = mediaSession?.player?.currentMediaItem?.mediaId ?: return
        serviceScope.launch {
            val dao = AppDatabase.get(this@PlaybackService).favoriteDao()
            if (dao.isFavorite(mediaId)) dao.remove(mediaId)
            else dao.add(FavoriteEntity(mediaId))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "正在播放",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "音乐播放控制"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}