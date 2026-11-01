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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
 */
@Composable
internal fun ShareEditorScreen(
    viewModel: AppViewModel,
    state: AppState,
    share: Share,
    onClose: () -> Unit,
) {
    var name by remember(share.id) { mutableStateOf(share.name) }
    var urlPath by remember(share.id) { mutableStateOf(share.urlPath) }
    // A grant with no permissions is ignored by the renderer; dropping it here keeps the editor
    // showing exactly what the generated config will contain.
    var grants by remember(share.id) {
        mutableStateOf(share.grants.filter { it.permissions.isNotEmpty() })
    }

    val draft = share.copy(name = name, urlPath = urlPath, grants = grants)
    val index = state.shares.indexOfFirst { it.id == share.id }
    val candidate = state.copy(
        shares = if (index >= 0) {
            state.shares.toMutableList().also { it[index] = draft }
        } else {
            state.shares + draft
        },
    )
    val draftPath = "shares[${if (index >= 0) index else state.shares.size}]"
    val draftErrors = Validator.validate(candidate).filter { issue ->
        issue.severity == Severity.ERROR &&
            (issue.path == draftPath || issue.path.startsWith("$draftPath."))
    }
    val anyone = grants.any { it.isEveryone && it.permissions.isNotEmpty() }

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
                onClick = {
                    viewModel.saveShare(draft.copy(grants = grants.filter { it.permissions.isNotEmpty() }))
                    onClose()
                },
                modifier = Modifier.testTag(Tags.ShareEditorSave),
            ) {
                Text("Save")
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = urlPath,
            onValueChange = { urlPath = it },
            label = { Text("Link path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(Tags.ShareLinkField),
        )
        Text(
            text = "Address: ${ConfigRenderer.mountPath(urlPath)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
                    onCheckedChange = { on -> grants = setEveryone(grants, on) },
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
                                checked = grants
                                    .firstOrNull { it.principal == user.name }
                                    ?.permissions
                                    ?.contains(permission) == true,
                                onCheckedChange = { on ->
                                    grants = setPermission(grants, user.name, permission, on)
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
    }
}

private fun setEveryone(grants: List<Grant>, on: Boolean): List<Grant> = if (on) {
    if (grants.any { it.isEveryone }) {
        grants.map {
            if (it.isEveryone) {
                it.copy(permissions = it.permissions + Permission.VIEW)
            } else {
                it
            }
        }
    } else {
        grants + Grant(Grant.EVERYONE, setOf(Permission.VIEW))
    }
} else {
    grants.filterNot { it.isEveryone }
}

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
