package com.example.musicplayer.data.model

import android.net.Uri

/**
 * 本地音频文件模型。所有字段都来自系统 MediaStore，绝对不涉及任何网络数据。
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long = 0,
)
