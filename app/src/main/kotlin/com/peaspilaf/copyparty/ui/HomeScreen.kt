package com.peaspilaf.copyparty.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.Validator
import com.peaspilaf.copyparty.data.FileImporter
import com.peaspilaf.copyparty.server.LocalUrls
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.server.rememberNotificationPermissionRequester
import kotlinx.coroutines.launch

/**
 * "Is this phone serving files right now?" One look answers that, one tap changes it, and a
 * configuration that cannot work says why before the server is started.
 *
 * A fresh install is not a bare Start button: it is a two-step checklist whose first step really
 * opens the folder picker, so the first tap a novice makes cannot be one that is guaranteed to fail.
 */
@Composable
internal fun HomeScreen(
    viewModel: AppViewModel,
    state: AppState,
    showRestartToApply: Boolean,
    onChooseFolder: () -> Unit,
    onBrowseFiles: () -> Unit,
    onShowQr: (String) -> Unit,
    onOpenLog: () -> Unit,
    onOpenExpert: () -> Unit,
    onRestartToApply: () -> Unit,
    restartFailure: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val status by viewModel.serverStatus.collectAsState()
    val current = status

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { documents ->
        if (documents.isNotEmpty()) {
            // Copying a photo library takes long enough to freeze the UI if it ran on the main thread.
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    FileImporter(context.contentResolver)
                        .import(documents, viewModel.storage.appShareRoot())
                }
                snackbar.showSnackbar(result.summary)
            }
        }
    }

    // The "enable a shared folder" advice is the checklist's job, not an error to shout about: it is
    // not a setting the user got wrong, it is the first step they have not taken yet.
    val errors = Validator.validate(state)
        .filter { it.severity == Severity.ERROR && it.path != "shares" }
    val shareable = state.shares.any { it.enabled }
    val canStart = errors.isEmpty() && shareable &&
        (current is ServerStatus.Stopped || current is ServerStatus.Failed)
    val runningUrl = (current as? ServerStatus.Running)?.urls?.firstOrNull()
    // The phone browses itself over loopback, but a QR code or a copied link is for *another*
    // device: hand out the LAN address when there is one, exactly as the server notification does.
    // The displayed list still starts with loopback, because that is the phone's own address.
    val clientUrl = (current as? ServerStatus.Running)?.let {
        LocalUrls.clientUrl(it.urls, state.settings.port)
    }
    val firstShare = state.shares.firstOrNull { it.enabled }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag(Tags.ScreenHome),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Copyparty", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Turn this phone into a file server. Share a folder, then start it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = statusLabel(current),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(Tags.ServerStatus),
                    )
                    when (current) {
                        is ServerStatus.Running -> {
                            Text(
                                text = "Open one of these addresses in a browser:",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (current.urls.isEmpty()) {
                                Text(
                                    text = "Waiting for a network address\u2026",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            for ((index, url) in current.urls.withIndex()) {
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = if (index == 0) {
                                        Modifier.testTag(Tags.HomeAddress)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                        is ServerStatus.Failed -> Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Unit
                    }
                }
            }

            if (!shareable) {
                FirstRunChecklist(onChooseFolder = onChooseFolder)
            }

            if (showRestartToApply) {
                RestartToApplyBanner(onRestartToApply = onRestartToApply, failure = restartFailure)
            }

            if (errors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag(Tags.ValidationBanner),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Check your setup",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        for (issue in errors) {
                            Text(
                                text = "\u2022 ${issue.message}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        // From Android 13 the running notification needs a permission; ask before
                        // the service starts, not after it is already silent.
                        requestNotificationPermission()
                        viewModel.startServer()
                    },
                    enabled = canStart,
                    modifier = Modifier.weight(1f).testTag(Tags.HomeStart),
                ) {
                    Text("Start server")
                }
                if (current is ServerStatus.Running || current is ServerStatus.Starting) {
                    OutlinedButton(
                        onClick = { viewModel.stopServer() },
                        modifier = Modifier.weight(1f).testTag(Tags.HomeStop),
                    ) {
                        Text("Stop server")
                    }
                }
            }
            if (!shareable) {
                Text(
                    text = "Choose a folder first, then this button starts the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (runningUrl != null) {
                val address = clientUrl ?: runningUrl
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (firstShare != null) {
                        Button(
                            onClick = onBrowseFiles,
                            modifier = Modifier.weight(1f).testTag(Tags.HomeBrowse),
                        ) {
                            Text("Browse files")
                        }
                    }
                    OutlinedButton(
                        onClick = { onShowQr(address) },
                        modifier = Modifier.weight(1f).testTag(Tags.HomeQr),
                    ) {
                        Text("QR code")
                    }
                }
                OutlinedButton(
                    onClick = {
                        copyAddress(context, address)
                        scope.launch { snackbar.showSnackbar("Address copied") }
                    },
                    modifier = Modifier.fillMaxWidth().testTag(Tags.HomeCopy),
                ) {
                    Text("Copy link")
                }
            }

            Text(
                text = shareSummary(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f).testTag(Tags.ImportFiles),
                ) {
                    Text("Add files")
                }
                OutlinedButton(
                    onClick = onOpenLog,
                    modifier = Modifier.weight(1f).testTag(Tags.HomeServerLog),
                ) {
                    Text("Server log")
                }
            }
            Text(
                text = "Add files copies documents into the app's own folder, so the server has " +
                    "something to serve on this build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.settings.expertMode) {
                TextButton(
                    onClick = onOpenExpert,
                    modifier = Modifier.fillMaxWidth().testTag(Tags.ExpertEntry),
                ) {
                    Text("Expert tools")
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The two steps a fresh install has to take, with the first one a real action. */
@Composable
private fun FirstRunChecklist(onChooseFolder: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(Tags.FirstRunChecklist)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Two steps to serve a folder",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = "1. Choose a folder", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Pick the folder this phone will serve. The app's own folder always works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onChooseFolder,
                modifier = Modifier.testTag(Tags.FirstRunChooseFolder),
            ) {
                Text("Choose a folder")
            }
            Text(text = "2. Start serving", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Tap Start server. The address to type on another device appears here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shareSummary(state: AppState): String {
    val enabled = state.shares.count { it.enabled }
    return when (enabled) {
        0 -> "No folders are shared yet."
        1 -> "1 folder is shared."
        else -> "$enabled folders are shared."
    }
}
