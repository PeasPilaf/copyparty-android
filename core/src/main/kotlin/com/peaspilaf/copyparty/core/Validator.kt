package com.peaspilaf.copyparty.core

enum class Severity { ERROR, WARNING }

/**
 * A single problem with the configuration. [path] points at the offending value, for example
 * `shares[2].localPath`, so the UI can show the message next to the field.
 */
data class Issue(val severity: Severity, val path: String, val message: String)

/**
 * Validates an [AppState] before it is saved or rendered. ERRORs must be fixed; WARNINGs are
 * allowed but call out a probably-unintended setup.
 */
object Validator {
    val USER_NAME = Regex("^[A-Za-z0-9_.-]{1,64}$")
    val URL_PATH = Regex("^[A-Za-z0-9._-]{1,64}$")
    val SERVER_NAME = Regex("^[A-Za-z0-9 ._-]{0,64}$")
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    fun validate(state: AppState): List<Issue> {
        val issues = mutableListOf<Issue>()

        if (state.settings.port !in MIN_PORT..MAX_PORT) {
            issues += Issue(Severity.ERROR, "settings.port", "Port must be between $MIN_PORT and $MAX_PORT.")
        }
        if (!SERVER_NAME.matches(state.settings.serverName)) {
            issues += Issue(
                Severity.ERROR,
                "settings.serverName",
                "Server name may only contain letters, digits, spaces and . _ -",
            )
        }

        validateUsers(state, issues)
        validateShares(state, issues)

        if (state.shares.none { it.enabled }) {
            issues += Issue(Severity.ERROR, "shares", "Enable at least one shared folder.")
        }

        return issues
    }

    private fun validateUsers(state: AppState, issues: MutableList<Issue>) {
        val seen = mutableSetOf<String>()
        state.users.forEachIndexed { index, user ->
            if (!USER_NAME.matches(user.name)) {
                issues += Issue(
                    Severity.ERROR,
                    "users[$index].name",
                    "User name may only contain letters, digits and . _ -",
                )
            }
            if (!seen.add(user.name)) {
                issues += Issue(Severity.ERROR, "users[$index].name", "Duplicate user name \"${user.name}\".")
            }
            if (user.password.isEmpty()) {
                issues += Issue(Severity.ERROR, "users[$index].password", "Password must not be empty.")
            }
            if (user.password.contains(':') || user.password.any { it.isISOControl() }) {
                issues += Issue(
                    Severity.ERROR,
                    "users[$index].password",
                    "Password must not contain ':' or line breaks.",
                )
            }
        }
    }

    private fun validateShares(state: AppState, issues: MutableList<Issue>) {
        val userIds = state.users.map { it.name }.toSet()
        val seenIds = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()

        state.shares.forEachIndexed { index, share ->
            val at = "shares[$index]"
            if (!seenIds.add(share.id)) {
                issues += Issue(Severity.ERROR, "$at.id", "Duplicate share id.")
            }
            if (!URL_PATH.matches(share.urlPath) || share.urlPath.trim('/').isEmpty()) {
                issues += Issue(
                    Severity.ERROR,
                    "$at.urlPath",
                    "Link path may only contain letters, digits and . _ -",
                )
            } else if (!seenUrls.add(share.urlPath.lowercase())) {
                issues += Issue(Severity.ERROR, "$at.urlPath", "Link path is already used by another share.")
            }
            if (!share.localPath.startsWith("/") || share.localPath.any { it.isISOControl() }) {
                issues += Issue(Severity.ERROR, "$at.localPath", "Pick an absolute folder path without line breaks.")
            }
            if (share.name.isBlank()) {
                issues += Issue(Severity.WARNING, "$at.name", "Give the share a name.")
            }

            share.grants.forEachIndexed { grantIndex, grant ->
                if (!grant.isEveryone && grant.principal !in userIds) {
                    issues += Issue(
                        Severity.ERROR,
                        "$at.grants[$grantIndex].principal",
                        "Unknown user \"${grant.principal}\".",
                    )
                }
                if (grant.permissions.isEmpty()) {
                    issues += Issue(
                        Severity.WARNING,
                        "$at.grants[$grantIndex].permissions",
                        "Grant has no permissions and will be ignored.",
                    )
                }
                if ((Permission.MOVE in grant.permissions || Permission.DELETE in grant.permissions) &&
                    Permission.UPLOAD !in grant.permissions
                ) {
                    issues += Issue(
                        Severity.WARNING,
                        "$at.grants[$grantIndex].permissions",
                        "Move/delete usually needs upload permission too.",
                    )
                }
            }

            if (share.grants.none { it.permissions.isNotEmpty() }) {
                issues += Issue(Severity.WARNING, "$at.grants", "Nobody can access this share.")
            }
        }
    }
}
