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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlaylistPlay
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            granted = true
            libraryVm.scan()
        } else {
            launcher.launch(permission)
        }
        // Android 13+ 请求通知权限（锁屏/通知栏媒体控制需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
        NowPlayingScreen(playerVm, libraryVm, favIds) { showSheet = false }
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
fun NowPlayingScreen(
    playerVm: PlayerViewModel,
    libraryVm: LibraryViewModel,
    favIds: Set<String>,
    onDismiss: () -> Unit,
) {
    val current by playerVm.currentTrack.collectAsState()
    val isPlaying by playerVm.isPlaying.collectAsState()
    val pos by playerVm.positionMs.collectAsState()
    val dur by playerVm.durationMs.collectAsState()
    val repeat by playerVm.repeatMode.collectAsState()
    val queue by playerVm.queue.collectAsState()
    val currentIndex by playerVm.currentIndex.collectAsState()
    val lyrics by playerVm.lyrics.collectAsState()
    var showQueue by remember { mutableStateOf(false) }

    val dragging = remember { mutableStateOf(false) }
    var sliderPos by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(pos) { if (!dragging.value) sliderPos = pos.toFloat() }

    current ?: return
    val isFav = favIds.contains(current!!.id)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF121212))) {
            // 专辑封面背景（上半屏）
            if (current!!.albumArtUri != null) {
                AsyncImage(
                    current!!.albumArtUri, null,
                    Modifier.fillMaxWidth().fillMaxHeight(0.55f),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.55f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MusicNote, null, Modifier.size(120.dp),
                        tint = Color.White.copy(alpha = 0.3f))
                }
            }
            // 渐变遮罩
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.1f),
                        0.35f to Color.Black.copy(alpha = 0.5f),
                        0.6f to Color(0xFF121212),
                        1f to Color(0xFF121212),
                    )
                )
            )

            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
            ) {
                // 顶栏
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.KeyboardArrowDown, "收起",
                            tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                // 歌名 + 收藏
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            current!!.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            current!!.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { libraryVm.toggleFavorite(current!!) }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "收藏",
                            tint = if (isFav) Color(0xFFE91E63) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 歌词
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (lyrics.isNotBlank()) {
                        Text(
                            lyrics,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    } else {
                        Text(
                            "暂无歌词",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 进度条
                Slider(
                    value = sliderPos,
                    onValueChange = { sliderPos = it; dragging.value = true },
                    onValueChangeFinished = { dragging.value = false; playerVm.seekTo(sliderPos.toLong()) },
                    valueRange = 0f..(dur.coerceAtLeast(1).toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatDuration(pos.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f))
                    Text(formatDuration(dur),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f))
                }

                Spacer(Modifier.height(16.dp))

                // 播放控制
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = playerVm::cycleRepeat, modifier = Modifier.size(48.dp)) {
                        val icon = if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                        Icon(
                            icon, null,
                            tint = if (repeat != Player.REPEAT_MODE_OFF) Color(0xFFE91E63) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    IconButton(onClick = playerVm::prev, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Filled.SkipPrevious, null,
                            tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    FloatingActionButton(
                        onClick = playerVm::togglePlay,
                        modifier = Modifier.size(64.dp),
                        containerColor = Color.White,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = playerVm::next, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Filled.SkipNext, null,
                            tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    IconButton(onClick = { showQueue = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.PlaylistPlay, "播放队列",
                            tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                    }
                }

                Spacer(Modifier.height(32.dp))
            }

            // 播放队列弹层
            if (showQueue) {
                QueueSheet(
                    queue = queue,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    onPick = { idx -> playerVm.playQueueIndex(idx) },
                    onDismiss = { showQueue = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "播放队列 (${queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "关闭", tint = Color.White)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            LazyColumn(Modifier.fillMaxHeight(0.5f)) {
                items(queue.size) { i ->
                    val t = queue[i]
                    val isCurrent = i == currentIndex
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { onPick(i) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) Color(0xFFE91E63) else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                t.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isPlaying && isCurrent) {
                            Text("正在播放",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE91E63))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
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