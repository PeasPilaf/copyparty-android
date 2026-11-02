package com.peaspilaf.copyparty.server

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Keeps the Python server alive and visible: a foreground service with a notification that shows
 * the address and offers Open and Stop actions. The activity is only the control surface.
 *
 * "Running" is only published after [ServerReadiness] has proved that copyparty itself answers on
 * the port and the interpreter is alive, so a foreign process holding the port can never be
 * presented as this app's server.
 *
 * Boot attempts are serialized: a restart stops the old interpreter and waits for the previous
 * attempt to finish before the new one starts, so a stop can never race a start for the port.
 */
class ServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopping = false

    /** Identifies the current boot attempt; an older attempt must not report state or notifications. */
    @Volatile
    private var generation = 0

    /** The attempt currently in flight, joined by the next one so their Python calls cannot overlap. */
    private var bootJob: Job? = null

    /** Serializes start/stop of the interpreter so a stop can never interleave with a start. */
    private val python = Mutex()

    override fun onCreate() {
        super.onCreate()
        ServerNotifications.createChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val attempt = ++generation
            scope.launch { shutdown(attempt) }
            return START_NOT_STICKY
        }

        val command = intent ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val configPath = command.getStringExtra(EXTRA_CONFIG_PATH)
        if (configPath.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val restart = command.action == ACTION_RESTART
        val port = command.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
        val lanAccess = command.getBooleanExtra(EXTRA_LAN_ACCESS, true)
        val attempt = ++generation
        val previous = bootJob
        stopping = false
        startForegroundCompat(ServerNotifications.starting(this))
        bootJob = scope.launch {
            // A restart must stop the old copyparty before the new one can bind the port. Stopping
            // it before joining also makes an in-flight readiness check give up instead of waiting
            // out its full budget.
            if (restart) {
                python.withLock { stopCopyparty() }
            }
            previous?.join()
            if (isStale(attempt)) return@launch
            boot(configPath, port, lanAccess, attempt)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // If the platform destroys the service (not our own stop path), the interpreter would keep
        // the sockets open with no notification to stop it. Shut it down off the main thread.
        Thread({ runCatching { PythonBridge.stop() } }, "copyparty-service-destroy").start()
        super.onDestroy()
    }

    private suspend fun boot(configPath: String, port: Int, lanAccess: Boolean, attempt: Int) {
        ServerState.set(ServerStatus.Starting)
        val configFile = File(configPath)
        val logFile = File(configFile.parentFile, "copyparty.log")
        val error = python.withLock {
            if (isStale(attempt)) {
                null
            } else {
                PythonBridge.start(this, configFile, logFile, File(filesDir, "copyparty/runtime"))
            }
        }
        if (isStale(attempt)) {
            // A stop arrived while the interpreter was coming up: do not leave it running with no
            // service to own it.
            python.withLock { stopCopyparty() }
            return
        }
        if (error != null) {
            fail(error, attempt)
            return
        }

        val result = ServerReadiness(port = port, lanAccess = lanAccess).await()
        if (isStale(attempt)) return
        when (result) {
            is ServerStatus.Running -> {
                ServerState.set(result)
                ServerNotifications.post(
                    this,
                    ServerNotifications.running(this, LocalUrls.clientUrl(result.urls, port)),
                )
            }

            is ServerStatus.Failed -> {
                python.withLock { stopCopyparty() }
                val message = if (result.message == ServerProbe.portInUseMessage(port)) {
                    // Somebody else owns the port: that is the useful sentence, not a bind trace.
                    result.message
                } else {
                    PythonBridge.lastError() ?: result.message
                }
                fail(message, attempt)
            }

            else -> fail("The server could not start.", attempt)
        }
    }

    /** True when a newer request (or a stop) has superseded this attempt. */
    private fun isStale(attempt: Int): Boolean = stopping || attempt != generation

    /** Publishes the foreground state; the address shown is [LocalUrls.clientUrl]'s choice. */
    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, ServerNotifications.RUNNING_NOTIFICATION_ID, notification, type)
    }

    /**
     * Stops the server for one request [attempt].
     *
     * A stop that a newer Start has already superseded does nothing at all: killing the interpreter
     * or removing the foreground notification then would take down the server the user just asked
     * for, and report it as a failure.
     */
    private suspend fun shutdown(attempt: Int) {
        if (attempt != generation) return
        stopping = true
        val current = ServerState.status.value
        if (current is ServerStatus.Running || current is ServerStatus.Starting) {
            ServerState.set(ServerStatus.Stopping)
        }
        python.withLock {
            // Checked again under the lock: a start may have arrived while this stop waited.
            if (attempt == generation) stopCopyparty()
        }
        if (attempt != generation) return
        ServerState.set(ServerStatus.Stopped)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Stops copyparty, retrying while the interpreter is still coming up.
     *
     * `PythonBridge.stop()` can only interrupt a server whose service loop already exists; during
     * the first seconds of a boot it is a no-op, and giving up there would leave the old server
     * holding the port (and the old config) behind the new one.
     */
    private suspend fun stopCopyparty() {
        val deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS
        while (PythonBridge.isAlive() && System.currentTimeMillis() < deadline) {
            PythonBridge.stop()
            if (!PythonBridge.isAlive()) return
            delay(STOP_RETRY_MS)
        }
    }

    /**
     * A failed start keeps a dismissible, tappable notification instead of deleting its own: the
     * user has to be able to see *why* the server is not there. A stale attempt says nothing.
     */
    private fun fail(message: String, attempt: Int) {
        if (isStale(attempt)) return
        ServerState.set(ServerStatus.Failed(message))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        ServerNotifications.post(this, ServerNotifications.failed(this, message))
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.peaspilaf.copyparty.action.START"
        const val ACTION_STOP = "com.peaspilaf.copyparty.action.STOP"

        /** Stop the old copyparty and boot the freshly rendered config, in one serial request. */
        const val ACTION_RESTART = "com.peaspilaf.copyparty.action.RESTART"

        const val EXTRA_CONFIG_PATH = "config_path"
        const val EXTRA_PORT = "port"
        const val EXTRA_LAN_ACCESS = "lan_access"

        private const val DEFAULT_PORT = 3923

        /** How long a restart/stop keeps trying to interrupt an interpreter that is still booting. */
        private const val STOP_TIMEOUT_MS = 15_000L
        private const val STOP_RETRY_MS = 100L
    }
}
