package com.example.musicplayer.lan

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把收到的字节流作为音频文件写入系统媒体库（Music/Received），
 * 写完后立即被本机播放器识别。仅本地写入，不联网。
 */
object MediaStoreWriter {

    private val AUDIO_EXT = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "oga", "opus", "ape", "wma",
    )

    fun isAudio(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXT

    fun save(context: Context, displayName: String, bytes: ByteArray): Boolean {
        if (!isAudio(displayName)) return false
        val mime = mimeFor(displayName)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Received")
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Received",
                )
                dir.mkdirs()
                put(MediaStore.Audio.Media.DATA, File(dir, displayName).absolutePath)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/ogg"
        "ape" -> "audio/x-monkeys-audio"
        "wma" -> "audio/x-ms-wma"
        else -> "audio/*"
    }
}
