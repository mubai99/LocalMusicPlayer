package com.example.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.lan.LanTransferController
import com.example.musicplayer.ui.screens.Root
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import com.example.musicplayer.viewmodel.LibraryViewModel
import com.example.musicplayer.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicPlayerTheme {
                val libraryVm: LibraryViewModel = viewModel()
                val playerVm: PlayerViewModel = viewModel()

                // 局域网收到新歌后，自动刷新本机曲库
                LaunchedEffect(Unit) {
                    LanTransferController.onFileReceived = { libraryVm.rescan() }
                }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(libraryVm, playerVm)
                }
            }
        }
    }
}
