package com.example.musicplayer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.lan.LanTransferController
import com.example.musicplayer.lan.LanTransferService
import com.example.musicplayer.viewmodel.LibraryViewModel

@Composable
fun LanScreen(libraryVm: LibraryViewModel) {
    val ctx = LocalContext.current
    val isRunning by LanTransferController.isRunning.collectAsState()
    val endpoint by LanTransferController.endpoint.collectAsState()
    val token by LanTransferController.token.collectAsState()
    val received by LanTransferController.receivedCount.collectAsState()
    val last by LanTransferController.lastReceived.collectAsState()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("局域网传歌", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "开启后，同一 Wi-Fi 下的电脑 / 手机用浏览器打开下方链接即可把歌曲传入本机。" +
                "文件会写入 Music/Received 并自动进入曲库。数据只在局域网内，不经公网。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("接收服务", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { on ->
                            if (on) {
                                ctx.startForegroundService(Intent(ctx, LanTransferService::class.java))
                            } else {
                                ctx.stopService(Intent(ctx, LanTransferService::class.java))
                            }
                        },
                    )
                }

                if (isRunning && endpoint != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("浏览器打开此链接：", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            endpoint!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { copyText(ctx, endpoint!!) }) {
                            Icon(Icons.Filled.ContentCopy, null)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "配对码：$token",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "已接收：$received 首" + (last?.let { "（最近：$it）" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "闲置 2 分钟自动关闭；也可点系统通知里的\u201c停止\u201d。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (!isRunning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "提示：请确认手机与目标设备连接在同一 Wi-Fi。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("endpoint", text))
}
