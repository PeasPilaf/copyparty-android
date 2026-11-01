package com.peaspilaf.copyparty.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.peaspilaf.copyparty.MainActivity
import com.peaspilaf.copyparty.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Keeps the Python server alive and visible: a foreground service with a notification that shows
 * the address and offers a stop action. The activity is only the control surface.
 */
class ServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { shutdown() }
            return START_NOT_STICKY
        }

        val configPath = intent?.getStringExtra(EXTRA_CONFIG_PATH)
        if (configPath.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        stopping = false
        val port = intent.getIntExtra(EXTRA_PORT, 3923)
        val lanAccess = intent.getBooleanExtra(EXTRA_LAN_ACCESS, true)

        startForegroundCompat(getString(R.string.notification_server_starting), null)
        scope.launch { boot(configPath, port, lanAccess) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun boot(configPath: String, port: Int, lanAccess: Boolean) {
        ServerState.set(ServerStatus.Starting)
        val configFile = File(configPath)
        val logFile = File(configFile.parentFile, "copyparty.log")
        val error = PythonBridge.start(this, configFile, logFile, File(filesDir, "copyparty/runtime"))
        if (error != null) {
            fail(error)
            return
        }
        val urls = awaitListening(port, lanAccess)
        if (urls == null) {
            val detail = PythonBridge.lastError()
            PythonBridge.stop()
            fail(detail ?: "The server did not start listening on port $port.")
            return
        }
        ServerState.set(ServerStatus.Running(urls, port))
        updateNotification(getString(R.string.notification_server_running), urls.firstOrNull())
    }

    private suspend fun awaitListening(port: Int, lanAccess: Boolean, timeoutMs: Long = 120_000): List<String>? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!stopping && System.currentTimeMillis() < deadline) {
            if (!PythonBridge.isAlive()) return null
            if (canConnect(port)) return LocalUrls.serverUrls(port, lanAccess)
            delay(150)
        }
        return null
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 500)
            true
        }
    }.getOrDefault(false)

    private suspend fun shutdown() {
        stopping = true
        val current = ServerState.status.value
        if (current is ServerStatus.Running || current is ServerStatus.Starting) {
            ServerState.set(ServerStatus.Stopping)
        }
        PythonBridge.stop()
        ServerState.set(ServerStatus.Stopped)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        ServerState.set(ServerStatus.Failed(message))
        runCatching { updateNotification(getString(R.string.notification_server_failed), message) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_server),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_server_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(title: String, text: String?) {
        val notification = buildNotification(title, text)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun updateNotification(title: String, text: String?) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopServer = PendingIntent.getService(
            this,
            1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(title)
            .setContentText(text.orEmpty())
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_action_stop), stopServer)
            .build()
    }

    companion object {
        const val ACTION_START = "com.peaspilaf.copyparty.action.START"
        const val ACTION_STOP = "com.peaspilaf.copyparty.action.STOP"
        const val EXTRA_CONFIG_PATH = "config_path"
        const val EXTRA_PORT = "port"
        const val EXTRA_LAN_ACCESS = "lan_access"

        private const val CHANNEL_ID = "server"
        private const val NOTIFICATION_ID = 1001
    }
}
