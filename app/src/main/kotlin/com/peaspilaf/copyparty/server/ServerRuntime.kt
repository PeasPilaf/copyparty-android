package com.peaspilaf.copyparty.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ServerStatus {
    data object Stopped : ServerStatus
    data object Starting : ServerStatus
    data class Running(val urls: List<String>, val port: Int) : ServerStatus
    data object Stopping : ServerStatus
    data class Failed(val message: String) : ServerStatus
}

/** Handle the UI and the foreground service use to start/stop the embedded copyparty server. */
interface ServerRuntime {
    val status: StateFlow<ServerStatus>
    fun start(): ServerStatus
    fun stop()

    /**
     * Re-render the config from the current store and restart the server so an edit takes effect.
     *
     * The status is told the truth: `Stopping` while the old server goes away, `Starting` once the
     * restart was handed to the service, then whatever the service reports. Calling it while the
     * server is stopped simply starts it; a configuration that cannot work is refused without
     * touching the running server. Safe to call twice.
     */
    fun applyConfig(): ServerStatus
}

/**
 * Process-wide server status. The foreground service writes it and the UI observes it, which keeps
 * the two decoupled and lets UI tests substitute a fake [ServerRuntime].
 */
object ServerState {
    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    fun set(status: ServerStatus) {
        _status.value = status
    }
}
