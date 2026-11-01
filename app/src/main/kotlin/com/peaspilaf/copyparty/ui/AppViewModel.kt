package com.peaspilaf.copyparty.ui

import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Issue
import com.peaspilaf.copyparty.core.ServerSettings
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
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val serverStatus: StateFlow<ServerStatus> get() = server.status

    fun update(transform: (AppState) -> AppState) {
        val next = transform(_state.value)
        _state.value = next
        store.save(next)
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

    fun startServer(): ServerStatus = server.start()

    fun stopServer() = server.stop()

    fun urls(): List<String> = (server.status.value as? ServerStatus.Running)?.urls.orEmpty()

    fun newShareId(): String = UUID.randomUUID().toString()

    fun randomPassword(length: Int = 16): String {
        val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    /** Grants for a new share default to "nobody" - explicit access is the point of the app. */
    fun newShare(localPath: String, name: String = "Shared folder"): Share = Share(
        id = newShareId(),
        name = name,
        urlPath = suggestUrlPath(name),
        localPath = localPath,
        grants = listOf(Grant(Grant.EVERYONE, emptySet())),
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
