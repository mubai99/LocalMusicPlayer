package com.example.musicplayer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApp
import com.example.musicplayer.data.db.FavoriteEntity
import com.example.musicplayer.data.db.PlaylistEntity
import com.example.musicplayer.data.db.PlaylistTrackEntity
import com.example.musicplayer.data.local.MusicScanner
import com.example.musicplayer.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = MusicScanner(application)
    private val db = (application as MusicPlayerApp).database

    private val prefs = application.getSharedPreferences("music_prefs", android.content.Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedFolders = MutableStateFlow<List<String>>(emptyList())
    val selectedFolders = _selectedFolders.asStateFlow()

    init {
        refreshFavorites()
        refreshPlaylists()
        loadFolders()
    }

    private fun loadFolders() {
        val saved = prefs.getStringSet("selected_folders", emptySet()) ?: emptySet()
        _selectedFolders.value = saved.toList()
    }

    fun addFolder(treeUri: Uri) {
        val existing = _selectedFolders.value.toMutableSet()
        existing.add(treeUri.toString())
        prefs.edit().putStringSet("selected_folders", existing).apply()
        _selectedFolders.value = existing.toList()
        scan()
    }

    fun removeFolder(uriStr: String) {
        val existing = _selectedFolders.value.toMutableSet()
        existing.remove(uriStr)
        prefs.edit().putStringSet("selected_folders", existing).apply()
        _selectedFolders.value = existing.toList()
        // 释放持久化权限
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(uriStr),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        scan()
    }

    fun scan() {
        _isLoading.value = true
        viewModelScope.launch {
            val mediaStoreTracks = runCatching { scanner.scan() }.getOrDefault(emptyList())
            val folderUris = _selectedFolders.value.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            val folderTracks = if (folderUris.isNotEmpty()) {
                runCatching { scanner.scanFolders(folderUris) }.getOrDefault(emptyList())
            } else emptyList()
            // 合并：MediaStore 在前，文件夹文件在后，按 ID 去重
            val merged = (folderTracks + mediaStoreTracks).distinctBy { it.id }
            _tracks.value = merged
            _isLoading.value = false
        }
    }

    /** 供局域网传歌回调使用：收到新文件后刷新曲库。 */
    fun rescan() = scan()

    fun refreshFavorites() {
        viewModelScope.launch { _favoriteIds.value = db.favoriteDao().getAll().toSet() }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val dao = db.favoriteDao()
            if (_favoriteIds.value.contains(track.id)) dao.remove(track.id)
            else dao.add(FavoriteEntity(track.id))
            refreshFavorites()
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch { _playlists.value = db.playlistDao().getAll() }
    }

    suspend fun createPlaylistSuspend(name: String): Long {
        val id = db.playlistDao().insert(PlaylistEntity(name = name))
        refreshPlaylists()
        return id
    }

    fun addToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            val existing = db.playlistDao().getTracks(playlistId)
            db.playlistDao().addTrack(PlaylistTrackEntity(playlistId, track.id, existing.size))
        }
    }

    /** 若存在同名歌单则直接添加，否则先创建再添加。 */
    fun ensurePlaylistAndAdd(name: String, track: Track) {
        viewModelScope.launch {
            val existing = _playlists.value.find { it.name == name }
            val id = existing?.id ?: createPlaylistSuspend(name)
            addToPlaylist(id, track)
        }
    }

    suspend fun getPlaylistTracks(playlistId: Long): List<Track> {
        val ordered = db.playlistDao().getTracks(playlistId)
        val map = _tracks.value.associateBy { it.id }
        return ordered.mapNotNull { map[it.trackId] }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { db.playlistDao().delete(playlist) }
    }
}
