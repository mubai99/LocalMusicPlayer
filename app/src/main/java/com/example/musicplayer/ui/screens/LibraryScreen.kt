package com.example.musicplayer.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.musicplayer.data.db.PlaylistEntity
import com.example.musicplayer.data.model.Track
import com.example.musicplayer.viewmodel.LibraryViewModel
import com.example.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun Root(libraryVm: LibraryViewModel, playerVm: PlayerViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        granted = g
        if (g) libraryVm.scan()
    }
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            granted = true
            libraryVm.scan()
        } else {
            launcher.launch(permission)
        }
    }

    if (granted) AppContent(libraryVm, playerVm)
    else PermissionDenied { launcher.launch(permission) }
}

@Composable
fun AppContent(libraryVm: LibraryViewModel, playerVm: PlayerViewModel) {
    val tracks by libraryVm.tracks.collectAsState()
    val favIds by libraryVm.favoriteIds.collectAsState()
    val playlists by libraryVm.playlists.collectAsState()
    val current by playerVm.currentTrack.collectAsState()
    var tab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (current != null) MiniPlayer(playerVm) { showSheet = true }
            else Spacer(Modifier)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("歌曲") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("收藏") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("歌单") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("传歌") })
            }
            when (tab) {
                0 -> SongListTab(tracks, favIds, libraryVm, playerVm)
                1 -> TrackList(tracks.filter { it.id in favIds }, favIds, libraryVm, playerVm)
                2 -> PlaylistList(playlists, libraryVm, playerVm)
                3 -> LanScreen(libraryVm)
            }
        }
    }

    if (showSheet && current != null) {
        NowPlayingSheet(playerVm) { showSheet = false }
    }
}

@Composable
private fun SongListTab(
    tracks: List<Track>,
    favIds: Set<String>,
    libraryVm: LibraryViewModel,
    playerVm: PlayerViewModel,
) {
    val context = LocalContext.current
    val folders by libraryVm.selectedFolders.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            libraryVm.addFolder(uri)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 文件夹选择栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { folderPicker.launch(null) }) {
                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加文件夹")
            }
        }

        // 已选文件夹
        if (folders.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(folders.size) { i ->
                    val f = folders[i]
                    val name = Uri.decode(f.substringAfterLast('%').substringAfter(':'))
                        .takeLast(30)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { libraryVm.removeFolder(f) },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    "移除",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        TrackList(tracks, favIds, libraryVm, playerVm)
    }
}

@Composable
fun TrackList(
    tracks: List<Track>,
    favIds: Set<String>,
    libraryVm: LibraryViewModel,
    playerVm: PlayerViewModel,
) {
    val isLoading by libraryVm.isLoading.collectAsState()
    var addTarget by remember { mutableStateOf<Track?>(null) }

    if (isLoading && tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.MusicNote,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "暂无本地音乐",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "点击上方「添加文件夹」选择音乐目录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        items(tracks.size, key = { tracks[it].id }) { i ->
            val track = tracks[i]
            TrackRow(
                track = track,
                isFavorite = favIds.contains(track.id),
                onClick = { playerVm.playTracks(tracks, i) },
                onFav = { libraryVm.toggleFavorite(track) },
                onLong = { addTarget = track },
            )
            HorizontalDivider()
        }
    }

    addTarget?.let { target -> AddToPlaylistDialog(target, libraryVm) { addTarget = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit,
    onLong: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (track.albumArtUri != null) {
                AsyncImage(track.albumArtUri, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Icon(
                    Icons.Filled.MusicNote, null,
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${track.artist} · ${track.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onFav) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
}

@Composable
fun PlaylistList(
    playlists: List<PlaylistEntity>,
    libraryVm: LibraryViewModel,
    playerVm: PlayerViewModel,
) {
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有歌单，点右下角新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                items(playlists.size, key = { playlists[it].id.toInt() }) { i ->
                    val pl = playlists[i]
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val ts = libraryVm.getPlaylistTracks(pl.id)
                                    if (ts.isNotEmpty()) playerVm.playTracks(ts, 0)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(pl.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { libraryVm.deletePlaylist(pl) }) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        FloatingActionButton(
            onClick = { showNew = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, null) }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        scope.launch { libraryVm.createPlaylistSuspend(newName) }
                        newName = ""
                        showNew = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("取消") } },
        )
    }
}

@Composable
fun AddToPlaylistDialog(track: Track, libraryVm: LibraryViewModel, onDismiss: () -> Unit) {
    val playlists by libraryVm.playlists.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            Column {
                playlists.forEach { pl ->
                    TextButton(
                        onClick = { libraryVm.addToPlaylist(pl.id, track); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(pl.name, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start) }
                }
                if (showNew) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("新歌单名称") }, singleLine = true)
                    Button(onClick = {
                        if (newName.isNotBlank()) {
                            libraryVm.ensurePlaylistAndAdd(newName, track)
                            onDismiss()
                        }
                    }) { Text("创建并添加") }
                } else {
                    TextButton(onClick = { showNew = true }, Modifier.fillMaxWidth()) {
                        Text("+ 新建歌单", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
fun MiniPlayer(playerVm: PlayerViewModel, onExpand: () -> Unit) {
    val current by playerVm.currentTrack.collectAsState()
    val isPlaying by playerVm.isPlaying.collectAsState()
    current ?: return

    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (current!!.albumArtUri != null) {
                    AsyncImage(current!!.albumArtUri, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(
                        Icons.Filled.MusicNote, null,
                        modifier = Modifier.align(Alignment.Center).size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(current!!.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(current!!.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = playerVm::togglePlay) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(playerVm: PlayerViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current by playerVm.currentTrack.collectAsState()
    val isPlaying by playerVm.isPlaying.collectAsState()
    val pos by playerVm.positionMs.collectAsState()
    val dur by playerVm.durationMs.collectAsState()
    val shuffle by playerVm.shuffle.collectAsState()
    val repeat by playerVm.repeatMode.collectAsState()

    val dragging = remember { mutableStateOf(false) }
    var sliderPos by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(pos) { if (!dragging.value) sliderPos = pos.toFloat() }

    current ?: return

    val gradientColors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(gradientColors))
                .padding(horizontal = 24.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))

                // 大封面
                Box(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .aspectRatio(1f)
                        .shadow(24.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (current!!.albumArtUri != null) {
                        AsyncImage(
                            current!!.albumArtUri, null,
                            Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote, null,
                            modifier = Modifier.align(Alignment.Center).size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // 歌名 + 艺术家
                Text(
                    current!!.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    current!!.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                // 进度条
                Slider(
                    value = sliderPos,
                    onValueChange = { sliderPos = it; dragging.value = true },
                    onValueChangeFinished = { dragging.value = false; playerVm.seekTo(sliderPos.toLong()) },
                    valueRange = 0f..(dur.coerceAtLeast(1).toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatDuration(pos.toLong()), style = MaterialTheme.typography.labelMedium)
                    Text(formatDuration(dur), style = MaterialTheme.typography.labelMedium)
                }

                Spacer(Modifier.height(16.dp))

                // 播放控制
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 随机
                    IconButton(onClick = playerVm::toggleShuffle, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Filled.Shuffle, null,
                            tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    // 上一首
                    IconButton(onClick = playerVm::prev, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipPrevious, null, Modifier.size(40.dp))
                    }
                    // 播放/暂停 — 大圆形按钮
                    FloatingActionButton(
                        onClick = playerVm::togglePlay,
                        modifier = Modifier.size(64.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    // 下一首
                    IconButton(onClick = playerVm::next, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipNext, null, Modifier.size(40.dp))
                    }
                    // 循环
                    IconButton(onClick = playerVm::cycleRepeat, modifier = Modifier.size(44.dp)) {
                        val icon = if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                        Icon(
                            icon, null,
                            tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PermissionDenied(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.MusicNote, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("需要读取本机音乐权限", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("本应用仅读取本地音频，不联网、不上传。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onRetry() }) { Text("授予权限") }
    }
}
