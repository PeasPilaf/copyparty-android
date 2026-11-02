package com.peaspilaf.copyparty.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Share
import kotlinx.coroutines.launch

/**
 * Every exposed folder is one readable row: its name, the link people really open, who is allowed
 * in, and a switch that takes it off the air without deleting it.
 *
 * A share can also be removed - behind a confirmation that names the folder and says its files are
 * kept - because "off" is not the same as "gone". The empty state carries the action that resolves
 * it, so deleting the last folder never leaves a screen with nothing to do on it.
 */
@Composable
internal fun SharesScreen(
    state: AppState,
    runningUrl: String?,
    showRestartToApply: Boolean,
    onAdd: () -> Unit,
    onEdit: (Share) -> Unit,
    onBrowse: (Share) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onDeleteConfirmed: (Share) -> Unit,
    onRestartToApply: () -> Unit,
    restartFailure: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<Share?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
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

            if (showRestartToApply) {
                RestartToApplyBanner(onRestartToApply = onRestartToApply, failure = restartFailure)
            }

            if (state.shares.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No folders yet. A shared folder is the thing other devices can open.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = onAdd,
                            modifier = Modifier.testTag(Tags.EmptyAddShare),
                        ) {
                            Text("Add a folder")
                        }
                    }
                }
            }

            for (share in state.shares) {
                ShareCard(
                    share = share,
                    runningUrl = runningUrl,
                    onEdit = onEdit,
                    onBrowse = onBrowse,
                    onEnabledChange = onEnabledChange,
                    onCopy = {
                        if (runningUrl == null) {
                            // A bare "/photos" is not a link anyone can open. Say so instead of
                            // copying something useless and claiming success.
                            scope.launch {
                                snackbar.showSnackbar("Start the server to get a shareable link.")
                            }
                        } else {
                            copyAddress(context, shareLink(share, runningUrl))
                            scope.launch { snackbar.showSnackbar("Link copied") }
                        }
                    },
                    onSend = {
                        if (runningUrl == null) {
                            scope.launch {
                                snackbar.showSnackbar("Start the server to get a shareable link.")
                            }
                        } else {
                            shareToAnotherApp(context, shareLink(share, runningUrl))
                            scope.launch { snackbar.showSnackbar("Link shared") }
                        }
                    },
                    onDelete = { deleting = share },
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val pending = deleting
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove this share?") },
            text = {
                // The tag lives on the container so tests can assert the message, not the dialog chrome.
                Column(modifier = Modifier.testTag(Tags.ShareDeleteDialog)) {
                    Text(
                        text = "\"${pending.name}\" stops being served when the server restarts. " +
                            "The files in ${pending.localPath} are kept.",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDeleteConfirmed(pending)
                    },
                    modifier = Modifier.testTag(Tags.ShareDeleteConfirm),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    modifier = Modifier.testTag(Tags.ShareDeleteCancel),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ShareCard(
    share: Share,
    runningUrl: String?,
    onEdit: (Share) -> Unit,
    onBrowse: (Share) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onCopy: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
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
                        text = shareLink(share, runningUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .testTag(Tags.ShareUrl)
                            .clickable { onBrowse(share) },
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
                TextButton(onClick = onCopy, modifier = Modifier.testTag(Tags.ShareCopy)) {
                    Text("Copy link")
                }
                TextButton(onClick = onSend, modifier = Modifier.testTag(Tags.ShareSend)) {
                    Text("Share")
                }
                TextButton(onClick = { onEdit(share) }, modifier = Modifier.testTag(Tags.ShareEdit)) {
                    Text("Edit access")
                }
                TextButton(onClick = onDelete, modifier = Modifier.testTag(Tags.ShareDelete)) {
                    Text("Delete")
                }
            }
        }
    }
}

/**
 * The one address a person can type for [share]: the running server's address plus the folder's
 * mount path. While the server is stopped the path is still shown, so the row never looks empty.
 */
internal fun shareLink(share: Share, runningUrl: String?): String {
    val path = ConfigRenderer.mountPath(share.urlPath)
    return runningUrl?.trimEnd('/')?.plus(path) ?: path
}

/** Hands the link to another app through the system chooser. */
private fun shareToAnotherApp(context: android.content.Context, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Copyparty link")
        putExtra(Intent.EXTRA_TEXT, link)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share link")) }
}

/** Who may open [share], for the row summary. Empty grants never reach copyparty, so they are left out. */
internal fun accessSummary(share: Share): String {
    val names = share.grants
        .filter { it.permissions.isNotEmpty() }
        .map { if (it.isEveryone) "Everyone" else it.principal }
        .distinct()
    return if (names.isEmpty()) "nobody" else names.joinToString(", ")
}
