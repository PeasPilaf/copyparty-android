package com.peaspilaf.copyparty.ui

import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Issue
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.core.Validator
import com.peaspilaf.copyparty.data.ConfigStore
import com.peaspilaf.copyparty.data.StorageAccess
import com.peaspilaf.copyparty.server.ServerRuntime
import com.peaspilaf.copyparty.server.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID

/**
 * Single source of truth for the UI: persists [AppState] through [ConfigStore] and delegates server
 * lifecycle to [ServerRuntime]. Plain class (not a ViewModel) so Compose tests can construct it with
 * fakes without an Android lifecycle.
 */
class AppViewModel(
    private val store: ConfigStore,
    private val server: ServerRuntime,
    val storage: StorageAccess,
) {
    private val initial = store.load()
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AppState> = _state.asStateFlow()

    /**
     * The state the running server was last handed. An edit that changes the rendered config makes
     * [pendingApply] true, which is the honest "saved, but not live yet" banner: the app never
     * claims a change took effect while the old server is still serving the old config.
     */
    private var appliedState: AppState = initial
    private val _pendingApply = MutableStateFlow(false)
    val pendingApply: StateFlow<Boolean> = _pendingApply.asStateFlow()

    /** Why the last "restart to apply" attempt failed, so the banner can say it instead of nothing. */
    private val _applyError = MutableStateFlow<String?>(null)
    val applyError: StateFlow<String?> = _applyError.asStateFlow()

    val serverStatus: StateFlow<ServerStatus> get() = server.status

    fun update(transform: (AppState) -> AppState) {
        val next = transform(_state.value)
        _state.value = next
        store.save(next)
        // Only a change that really alters the generated config needs a restart; a theme or an
        // expert-mode toggle is a screen setting, not a server setting.
        _pendingApply.value = ConfigRenderer.render(next) != ConfigRenderer.render(appliedState)
    }

    /**
     * Hands the saved configuration to the server again, and only clears the banner if it took it.
     * A runtime that cannot restart at all is reported as a failed start instead of crashing the
     * screen: the banner staying up is the truth, because the old config is still live.
     */
    fun applyIfSupported(): ServerStatus {
        _applyError.value = null

        // A configuration the validator refuses can never be handed to copyparty. If the only
        // problem is that nothing is shareable any more (the user deleted the last share), the
        // honest state is "stopped": leaving the old server running would keep serving a folder
        // that no longer exists in the app, under a "running" label.
        val errors = Validator.validate(_state.value).filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            val nothingShareable = errors.all { it.path == "shares" }
            if (nothingShareable && server.status.value is ServerStatus.Running) {
                server.stop()
                appliedState = _state.value
                _pendingApply.value = false
                return ServerStatus.Stopped
            }
            val failure = ServerStatus.Failed(errors.first().message)
            _applyError.value = failure.message
            return failure
        }

        val result = runCatching { server.applyConfig() }.getOrElse { error ->
            ServerStatus.Failed(error.message ?: "The server could not be restarted.")
        }
        if (result !is ServerStatus.Failed) {
            appliedState = _state.value
            _pendingApply.value = false
        } else {
            _applyError.value = result.message
        }
        return result
    }

    fun addUser(name: String, password: String) = update { current ->
        current.copy(users = current.users + User(name.trim(), password))
    }

    fun updateUser(originalName: String, name: String, password: String) = update { current ->
        val newName = name.trim()
        current.copy(
            users = current.users.map { if (it.name == originalName) User(newName, password) else it },
            shares = current.shares.map { share ->
                share.copy(
                    grants = share.grants.map { grant ->
                        if (grant.principal == originalName) grant.copy(principal = newName) else grant
                    },
                )
            },
        )
    }

    fun deleteUser(name: String) = update { current ->
        current.copy(
            users = current.users.filterNot { it.name == name },
            shares = current.shares.map { share ->
                share.copy(grants = share.grants.filterNot { it.principal == name })
            },
        )
    }

    fun saveShare(share: Share) = update { current ->
        val exists = current.shares.any { it.id == share.id }
        current.copy(
            shares = if (exists) current.shares.map { if (it.id == share.id) share else it } else current.shares + share,
        )
    }

    fun deleteShare(id: String) = update { current ->
        current.copy(shares = current.shares.filterNot { it.id == id })
    }

    fun setShareEnabled(id: String, enabled: Boolean) = update { current ->
        current.copy(shares = current.shares.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun updateSettings(settings: ServerSettings) = update { current -> current.copy(settings = settings) }

    fun issues(): List<Issue> = Validator.validate(_state.value)

    /**
     * Starts the server with the current configuration, so nothing is pending afterwards.
     *
     * A start the runtime refuses keeps the banner up: the old server (if one was running) still has
     * the old config, and pretending the change was live is the lie this banner exists to prevent.
     */
    fun startServer(): ServerStatus {
        val result = server.start()
        if (result !is ServerStatus.Failed) {
            appliedState = _state.value
            _pendingApply.value = false
        }
        return result
    }

    fun stopServer() = server.stop()

    fun urls(): List<String> = (server.status.value as? ServerStatus.Running)?.urls.orEmpty()

    fun newShareId(): String = UUID.randomUUID().toString()

    fun randomPassword(length: Int = 16): String {
        val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    /**
     * The share a folder picked in the picker becomes: anyone on the network can open it, so the
     * very first save produces a working link instead of a folder nobody can see (the shipped bug).
     * The editor still lets the owner narrow that down before saving.
     */
    fun newShare(localPath: String, name: String = "Shared folder"): Share = Share(
        id = newShareId(),
        name = name,
        urlPath = suggestUrlPath(name),
        localPath = localPath,
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    fun suggestUrlPath(name: String, existing: Collection<String> = _state.value.shares.map { it.urlPath }): String {
        val base = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "share" }
            .take(40)
        var candidate = base
        var suffix = 2
        while (existing.any { it.equals(candidate, ignoreCase = true) }) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }
}
