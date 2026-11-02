package com.peaspilaf.copyparty.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.peaspilaf.copyparty.core.AppState
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
        val error = firstError(current)
        if (error != null) {
            val failed = ServerStatus.Failed(error)
            ServerState.set(failed)
            return failed
        }

        val configFile = writeConfig(current)
        ServerState.set(ServerStatus.Starting)
        return dispatch(ServerService.ACTION_START, current, configFile)
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

    /**
     * Apply an edit to a possibly-running server.
     *
     * The config is re-rendered *before* the service is asked to restart, and the restart is one
     * `ACTION_RESTART` intent rather than a stop racing a start: the service stops copyparty and
     * boots it again serially, so the new interpreter cannot try to bind a port the old one still
     * holds. The old URLs are never left on screen - `Stopping`/`Starting` do not carry any.
     */
    override fun applyConfig(): ServerStatus {
        val current = configStore.load()
        val error = firstError(current)
        if (error != null) {
            // Refuse without touching the running server: an edit that cannot work must not take
            // down the server that does.
            return ServerStatus.Failed(error)
        }

        val configFile = writeConfig(current)
        val previous = ServerState.status.value
        val wasActive = previous is ServerStatus.Running || previous is ServerStatus.Starting
        if (wasActive) ServerState.set(ServerStatus.Stopping)

        val status = dispatch(
            if (wasActive) ServerService.ACTION_RESTART else ServerService.ACTION_START,
            current,
            configFile,
        )
        if (status is ServerStatus.Starting) ServerState.set(ServerStatus.Starting)
        return status
    }

    /** First blocking validation problem, or null when the state can be rendered and served. */
    private fun firstError(state: AppState): String? =
        Validator.validate(state).firstOrNull { it.severity == Severity.ERROR }?.message

    /** Where copyparty reads its config and where the log screen reads its log: one directory. */
    private fun writeConfig(state: AppState): File {
        val dir = File(context.filesDir, "copyparty").apply { mkdirs() }
        val configFile = File(dir, "copyparty.conf")
        val logFile = File(dir, "copyparty.log")
        configFile.writeText(ConfigRenderer.render(state, logFile.absolutePath))
        return configFile
    }

    /** One service request; a system that refuses it becomes a failure with the reason. */
    private fun dispatch(action: String, state: AppState, configFile: File): ServerStatus {
        val intent = Intent(context, ServerService::class.java).apply {
            this.action = action
            putExtra(ServerService.EXTRA_CONFIG_PATH, configFile.absolutePath)
            putExtra(ServerService.EXTRA_PORT, state.settings.port)
            putExtra(ServerService.EXTRA_LAN_ACCESS, state.settings.lanAccess)
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
}
