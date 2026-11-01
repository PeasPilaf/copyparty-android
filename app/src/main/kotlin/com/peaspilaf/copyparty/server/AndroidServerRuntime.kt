package com.peaspilaf.copyparty.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.Validator
import com.peaspilaf.copyparty.data.ConfigStore
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Real [ServerRuntime]: renders the config, then hands the work to [ServerService]. */
class AndroidServerRuntime(
    private val context: Context,
    private val configStore: ConfigStore,
) : ServerRuntime {
    override val status: StateFlow<ServerStatus> get() = ServerState.status

    override fun start(): ServerStatus {
        val current = configStore.load()
        val errors = Validator.validate(current).filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            val failed = ServerStatus.Failed(errors.first().message)
            ServerState.set(failed)
            return failed
        }

        val dir = File(context.filesDir, "copyparty").apply { mkdirs() }
        val configFile = File(dir, "copyparty.conf")
        val logFile = File(dir, "copyparty.log")
        configFile.writeText(ConfigRenderer.render(current, logFile.absolutePath))

        ServerState.set(ServerStatus.Starting)
        val intent = Intent(context, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_CONFIG_PATH, configFile.absolutePath)
            putExtra(ServerService.EXTRA_PORT, current.settings.port)
            putExtra(ServerService.EXTRA_LAN_ACCESS, current.settings.lanAccess)
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            ServerStatus.Starting
        } catch (t: Throwable) {
            val failed = ServerStatus.Failed("Could not start the file server: ${t.message}")
            ServerState.set(failed)
            failed
        }
    }

    override fun stop() {
        if (ServerState.status.value is ServerStatus.Running || ServerState.status.value is ServerStatus.Starting) {
            ServerState.set(ServerStatus.Stopping)
        }
        val intent = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP }
        try {
            context.startService(intent)
        } catch (t: Throwable) {
            context.stopService(intent)
            ServerState.set(ServerStatus.Stopped)
        }
    }
}
