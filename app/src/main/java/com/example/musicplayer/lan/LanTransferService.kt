package com.example.musicplayer.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.musicplayer.R
import fi.iki.elonen.NanoHTTPD
import java.util.Random

/**
 * 局域网传歌前台服务：
 * - 在手机上启动一个仅监听局域网端口的 HTTP 服务；
 * - 显示通知（含链接与配对码）+ "停止"按钮；
 * - 闲置超过 2 分钟自动关闭，端口不长期暴露。
 */
class LanTransferService : Service() {

    private var server: LanHttpServer? = null
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { stopService() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopService()
            return START_NOT_STICKY
        }
        if (server == null) startServer()
        return START_STICKY
    }

    private fun startServer() {
        val ip = lanIp() ?: run {
            // 未连接 Wi-Fi，无法提供服务
            stopService()
            return
        }
        val token = genToken()
        server = LanHttpServer(0, token, this) { resetIdle() }
        try {
            server!!.start()
        } catch (e: Exception) {
            stopService()
            return
        }
        val port = server!!.listeningPort
        val endpoint = "http://$ip:$port?token=$token"
        LanTransferController.publishRunning(endpoint, token)
        startForeground(NOTIFY_ID, buildNotification(endpoint, token))
        resetIdle()
    }

    private fun resetIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, IDLE_MS)
    }

    private fun stopService() {
        idleHandler.removeCallbacks(idleRunnable)
        server?.stop()
        server = null
        LanTransferController.publishStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(idleRunnable)
        server?.stop()
        server = null
        LanTransferController.publishStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun lanIp(): String? {
        val wifi = getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ip = wifi?.connectionInfo?.ipAddress ?: 0
        if (ip == 0) return null
        return Formatter.formatIpAddress(ip)
    }

    private fun genToken(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = Random()
        return (1..6).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "局域网传歌",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "同一 Wi-Fi 下其他设备向本机传歌"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(endpoint: String, token: String): Notification {
        val stopIntent = Intent(this, LanTransferService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            },
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("传歌服务运行中")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("链接（其他设备浏览器打开）:\n$endpoint\n配对码: $token"),
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.musicplayer.LAN_STOP"
        private const val CHANNEL_ID = "lan_transfer"
        private const val NOTIFY_ID = 2002
        private const val IDLE_MS = 120_000L // 闲置 2 分钟自动关闭
    }
}
