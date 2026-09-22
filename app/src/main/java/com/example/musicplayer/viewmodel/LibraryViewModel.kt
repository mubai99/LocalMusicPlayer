package com.example.musicplayer.viewmodel

import android.app.Application
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

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        refreshFavorites()
        refreshPlaylists()
    }

    fun scan() {
        _isLoading.value = true
        viewModelScope.launch {
            _tracks.value = runCatching { scanner.scan() }.getOrDefault(emptyList())
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
