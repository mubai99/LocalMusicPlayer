package com.example.musicplayer.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.musicplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 MediaStore 扫描本机音频，以及手动选择文件夹中的音频文件。
 * 全程本地，无网络。
 */
class MusicScanner(private val context: Context) {

    private val audioExtensions = setOf(
        "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "ape", "alac",
    )

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        // 仅音乐类型且时长 > 30s
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} = ? AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("1", "30000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE LOCALIZED ASC"

        val albumArtBase = Uri.parse("content://media/external/audio/albumart")
        val tracks = mutableListOf<Track>()

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    tracks.add(
                        Track(
                            id = id.toString(),
                            title = cursor.getString(titleCol) ?: "未知",
                            artist = cursor.getString(artistCol) ?: "未知艺术家",
                            album = cursor.getString(albumCol) ?: "未知专辑",
                            albumId = albumId,
                            durationMs = cursor.getLong(durCol),
                            uri = ContentUris.withAppendedId(collection, id),
                            albumArtUri = ContentUris.withAppendedId(albumArtBase, albumId),
                            dateAdded = cursor.getLong(dateCol),
                        )
                    )
                }
            }
        tracks
    }

    /**
     * 从用户手动选择的文件夹（树 URI）递归扫描音频文件。
     */
    suspend fun scanFolders(treeUris: List<Uri>): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val seenUris = mutableSetOf<String>()

        for (treeUri in treeUris) {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            scanDirectory(tree, tracks, seenUris)
        }
        tracks
    }

    private fun scanDirectory(
        dir: DocumentFile,
        out: MutableList<Track>,
        seen: MutableSet<String>,
    ) {
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                scanDirectory(child, out, seen)
            } else if (child.isFile && isAudioFile(child.name)) {
                val uri = child.uri.toString()
                if (uri in seen) continue
                seen.add(uri)

                val name = child.name ?: "未知"
                val title = name.substringBeforeLast('.')
                out.add(
                    Track(
                        id = "folder_${uri.hashCode()}",
                        title = title,
                        artist = "未知艺术家",
                        album = "本地文件夹",
                        albumId = 0L,
                        durationMs = 0L,
                        uri = child.uri,
                        albumArtUri = null,
                        dateAdded = child.lastModified(),
                    )
                )
            }
        }
    }

    private fun isAudioFile(name: String?): Boolean {
        if (name == null) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }
}
