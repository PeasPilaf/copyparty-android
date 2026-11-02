package com.peaspilaf.copyparty.core

import kotlinx.serialization.Serializable

/**
 * One copyparty volume permission. The single-letter codes are copyparty's own ACL flags;
 * [flag] is what ends up in the generated `accs:` lines.
 */
@Serializable
enum class Permission(val flag: Char) {
    /** `r`: list folder contents and download files. */
    VIEW('r'),

    /** `g`: download files without being able to list folders. */
    DOWNLOAD('g'),

    /** `w`: upload files. */
    UPLOAD('w'),

    /** `m`: move / rename files and folders. */
    MOVE('m'),

    /** `d`: permanently delete files and folders. */
    DELETE('d'),

    /** `a`: see uploader IPs and reload server config. */
    ADMIN('a'),
}

/** An access rule: [principal] is [EVERYONE] (anonymous) or one of the configured user names. */
@Serializable
data class Grant(
    val principal: String,
    val permissions: Set<Permission> = emptySet(),
) {
    val isEveryone: Boolean get() = principal == EVERYONE

    companion object {
        const val EVERYONE = "*"
    }
}

@Serializable
data class User(
    val name: String,
    val password: String,
)

/**
 * A folder exposed to the network. [urlPath] is the mountpoint (`photos` -> `/photos`) and
 * [localPath] is the real path on this device.
 *
 * [extraVolFlags] is the expert escape hatch: raw copyparty volflags appended to this volume's
 * `flags:` line (`nodupe, nosub`, ...). It is empty for everyone who never opens expert mode, and a
 * state file written before expert mode existed loads with it empty.
 */
@Serializable
data class Share(
    val id: String,
    val name: String,
    val urlPath: String,
    val localPath: String,
    val grants: List<Grant> = emptyList(),
    val enabled: Boolean = true,
    val extraVolFlags: String = "",
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Global server settings.
 *
 * [expertMode] only decides which controls the screens show; it never changes the generated config,
 * so hiding the expert fields cannot silently drop what the power user already saved. [extraArgs]
 * holds raw copyparty global directives, one per line, appended to `[global]`.
 */
@Serializable
data class ServerSettings(
    val port: Int = 3923,
    val lanAccess: Boolean = true,
    val startOnBoot: Boolean = false,
    val serverName: String = "copyparty",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val expertMode: Boolean = false,
    val extraArgs: String = "",
)

/** Everything the app persists. This is the only state the generated copyparty config derives from. */
@Serializable
data class AppState(
    val version: Int = 1,
    val users: List<User> = emptyList(),
    val shares: List<Share> = emptyList(),
    val settings: ServerSettings = ServerSettings(),
)
