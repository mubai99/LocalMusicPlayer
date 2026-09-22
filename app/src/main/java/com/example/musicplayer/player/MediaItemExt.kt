package com.example.musicplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicplayer.data.model.Track

/** 把本地 Track 转换为 Media3 的 MediaItem，便于 ExoPlayer 播放与锁屏显示。 */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build(),
    )
    .build()
