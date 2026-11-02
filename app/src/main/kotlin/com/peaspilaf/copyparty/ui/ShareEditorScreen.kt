package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.Validator

/**
 * One folder's permissions. Every tick here becomes one line of the generated copyparty config, so
 * a grant that cannot work (an unknown person, an unusable link path) is called out while editing.
 *
 * Two promises the shipped app broke live here: a share that grants nobody anything is refused
 * instead of silently saved invisible, and the folder can be seen and changed without losing the
 * permissions that were just set. Removing the share is one deliberate step behind a confirmation
 * that says the folder's files themselves are kept.
 */
@Composable
internal fun ShareEditorScreen(
    viewModel: AppViewModel,
    state: AppState,
    share: Share,
    onDraftChange: (Share) -> Unit,
    onChangeFolder: () -> Unit,
    onClose: () -> Unit,
) {
    var saveAttempted by remember(share.id) { mutableStateOf(false) }
    var confirmingDelete by remember(share.id) { mutableStateOf(false) }

    val index = state.shares.indexOfFirst { it.id == share.id }
    val candidate = state.copy(
        shares = if (index >= 0) {
            state.shares.toMutableList().also { it[index] = share }
        } else {
            state.shares + share
        },
    )
    val draftPath = "shares[${if (index >= 0) index else state.shares.size}]"
    val draftErrors = Validator.validate(candidate).filter { issue ->
        issue.severity == Severity.ERROR &&
            (issue.path == draftPath || issue.path.startsWith("$draftPath."))
    }
    val anyone = share.grants.any { it.isEveryone && it.permissions.isNotEmpty() }
    val nobodyCanOpen = share.grants.none { it.permissions.isNotEmpty() }
    val liveGrants = { share.grants.filter { it.permissions.isNotEmpty() } }

    fun save() {
        saveAttempted = true
        if (nobodyCanOpen) return
        viewModel.saveShare(share.copy(grants = liveGrants()))
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(Tags.ShareEditor),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag(Tags.ShareEditorBack)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Edit folder",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Button(
                onClick = { save() },
                modifier = Modifier.testTag(Tags.ShareEditorSave),
            ) {
                Text("Save")
            }
        }

        OutlinedTextField(
            value = share.name,
            onValueChange = { onDraftChange(share.copy(name = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = share.urlPath,
            onValueChange = { onDraftChange(share.copy(urlPath = it)) },
            label = { Text("Link path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(Tags.ShareLinkField),
        )
        Text(
            text = "Address: ${ConfigRenderer.mountPath(share.urlPath)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Folder",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = share.localPath,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(Tags.EditorFolder),
                )
                OutlinedButton(
                    onClick = onChangeFolder,
                    modifier = Modifier.testTag(Tags.EditorChangeFolder),
                ) {
                    Text("Change folder")
                }
            }
        }

        if (draftErrors.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().testTag(Tags.ShareIssues)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "This folder cannot work yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    for (issue in draftErrors) {
                        Text(
                            text = "\u2022 ${issue.message}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Text(text = "Who can open it?", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onDraftChange(share.copy(grants = withEveryone(share.grants, setOf(Permission.VIEW)))) },
                modifier = Modifier.weight(1f).testTag(Tags.PresetAnyoneView),
            ) {
                Text("Anyone can view")
            }
            OutlinedButton(
                onClick = { onDraftChange(share.copy(grants = share.grants.filterNot { it.isEveryone })) },
                modifier = Modifier.weight(1f).testTag(Tags.PresetPeople),
            ) {
                Text("Only people I choose")
            }
            OutlinedButton(
                onClick = { onDraftChange(share.copy(grants = withEveryone(share.grants, setOf(Permission.UPLOAD)))) },
                modifier = Modifier.weight(1f).testTag(Tags.PresetDropbox),
            ) {
                Text("Anyone can upload")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anyone on the network",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "No password needed. Anyone who can reach this phone can open the folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = anyone,
                    onCheckedChange = { on ->
                        onDraftChange(
                            share.copy(
                                grants = if (on) {
                                    withEveryone(share.grants, setOf(Permission.VIEW))
                                } else {
                                    share.grants.filterNot { it.isEveryone }
                                },
                            ),
                        )
                    },
                    modifier = Modifier.testTag(Tags.AnyoneOnNetwork),
                )
            }
        }

        Text(text = "People", style = MaterialTheme.typography.titleMedium)
        if (state.users.isEmpty()) {
            Text(
                text = "Add a person on the People tab to give them access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (user in state.users) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = user.name, style = MaterialTheme.typography.titleSmall)
                    for (permission in Permission.values()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = share.grants
                                    .firstOrNull { it.principal == user.name }
                                    ?.permissions
                                    ?.contains(permission) == true,
                                onCheckedChange = { on ->
                                    onDraftChange(
                                        share.copy(
                                            grants = setPermission(share.grants, user.name, permission, on),
                                        ),
                                    )
                                },
                                modifier = Modifier.testTag(Tags.permission(user.name, permission)),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = permissionLabel(permission),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (saveAttempted && nobodyCanOpen) {
            Card(modifier = Modifier.fillMaxWidth().testTag(Tags.EditorNoAccess)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Nobody can open this share",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Pick a preset above, or tick a permission for at least one person. " +
                            "A share nobody was granted access to does not appear to anyone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { confirmingDelete = true },
            modifier = Modifier.fillMaxWidth().testTag(Tags.EditorDelete),
        ) {
            Text("Delete this share")
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Remove this share?") },
            text = {
                Column(modifier = Modifier.testTag(Tags.EditorDeleteDialog)) {
                    Text(
                        text = "\"${share.name}\" (${share.localPath}) stops being served. " +
                            "The files in the folder are kept.",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.deleteShare(share.id)
                        onClose()
                    },
                    modifier = Modifier.testTag(Tags.EditorDeleteConfirm),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmingDelete = false },
                    modifier = Modifier.testTag(Tags.EditorDeleteCancel),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** "Anyone on the network" is one grant, so a preset replaces it instead of stacking duplicates. */
private fun withEveryone(grants: List<Grant>, permissions: Set<Permission>): List<Grant> =
    grants.filterNot { it.isEveryone } + Grant(Grant.EVERYONE, permissions)

private fun setPermission(
    grants: List<Grant>,
    principal: String,
    permission: Permission,
    on: Boolean,
): List<Grant> {
    if (grants.none { it.principal == principal }) {
        return if (on) grants + Grant(principal, setOf(permission)) else grants
    }
    return grants.map { grant ->
        if (grant.principal != principal) {
            grant
        } else {
            grant.copy(
                permissions = if (on) {
                    grant.permissions + permission
                } else {
                    grant.permissions - permission
                },
            )
        }
    }
}

private fun permissionLabel(permission: Permission): String = when (permission) {
    Permission.VIEW -> "View and download"
    Permission.DOWNLOAD -> "Download only"
    Permission.UPLOAD -> "Upload"
    Permission.MOVE -> "Move and rename"
    Permission.DELETE -> "Delete"
    Permission.ADMIN -> "Admin"
}
