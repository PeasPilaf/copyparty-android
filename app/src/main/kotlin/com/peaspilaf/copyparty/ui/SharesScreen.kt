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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Share

/**
 * Every exposed folder is one readable row: its name, the link people open, who is allowed in, and
 * a switch that takes it off the air without deleting it.
 */
@Composable
internal fun SharesScreen(
    shares: List<Share>,
    onAdd: () -> Unit,
    onEdit: (Share) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(Tags.ScreenShares),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Shared folders", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Each folder becomes a link on the server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAdd, modifier = Modifier.testTag(Tags.AddShare)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (shares.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "No folders yet. Tap Add to pick one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        for (share in shares) {
            ShareCard(share = share, onEdit = onEdit, onEnabledChange = onEnabledChange)
        }
    }
}

@Composable
private fun ShareCard(
    share: Share,
    onEdit: (Share) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(Tags.ShareRow)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = share.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(Tags.ShareName),
                    )
                    Text(
                        text = ConfigRenderer.mountPath(share.urlPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(Tags.SharePath),
                    )
                }
                Switch(
                    checked = share.enabled,
                    onCheckedChange = { onEnabledChange(share.id, it) },
                    modifier = Modifier.testTag(Tags.ShareEnabled),
                )
            }
            Text(
                text = "Who can open: ${accessSummary(share)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(Tags.ShareAccess),
            )
            Text(
                text = if (share.enabled) {
                    "Served while the server is running."
                } else {
                    "Off the air. The folder is kept."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onEdit(share) }, modifier = Modifier.testTag(Tags.ShareEdit)) {
                    Text("Edit access")
                }
            }
        }
    }
}

/** Who may open [share], for the row summary. Empty grants never reach copyparty, so they are left out. */
internal fun accessSummary(share: Share): String {
    val names = share.grants
        .filter { it.permissions.isNotEmpty() }
        .map { if (it.isEveryone) "Everyone" else it.principal }
        .distinct()
    return if (names.isEmpty()) "nobody" else names.joinToString(", ")
}
