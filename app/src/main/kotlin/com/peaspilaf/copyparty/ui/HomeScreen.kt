package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.Validator
import com.peaspilaf.copyparty.server.ServerStatus

/**
 * "Is this phone serving files right now?" One look answers that, one tap changes it, and a
 * configuration that cannot work says why before the server is started.
 */
@Composable
internal fun HomeScreen(
    viewModel: AppViewModel,
    state: AppState,
    onOpenBrowser: (String) -> Unit = {},
    onShowQr: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val status by viewModel.serverStatus.collectAsState()
    val current = status

    val errors = Validator.validate(state).filter { it.severity == Severity.ERROR }
    // "Enable at least one shared folder" is advice, not a reason to keep the server switched off:
    // the runtime refuses it and reports the message, and the app still has to be usable while the
    // user is setting the first folder up.
    val blocking = errors.filterNot { it.path == "shares" }
    val canStart = blocking.isEmpty() &&
        (current is ServerStatus.Stopped || current is ServerStatus.Failed)

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
                        for (url in current.urls) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.testTag(Tags.ServerUrl),
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
                onClick = { viewModel.startServer() },
                enabled = canStart,
                modifier = Modifier.weight(1f).testTag(Tags.StartServer),
            ) {
                Text("Start server")
            }
            if (current is ServerStatus.Running || current is ServerStatus.Starting) {
                OutlinedButton(
                    onClick = { viewModel.stopServer() },
                    modifier = Modifier.weight(1f).testTag(Tags.StopServer),
                ) {
                    Text("Stop server")
                }
            }
        }

        val runningUrl = (current as? ServerStatus.Running)?.urls?.firstOrNull()
        if (runningUrl != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onOpenBrowser(runningUrl) },
                    modifier = Modifier.weight(1f).testTag(Tags.OpenBrowser),
                ) {
                    Text("Open in app")
                }
                OutlinedButton(
                    onClick = { onShowQr(runningUrl) },
                    modifier = Modifier.weight(1f).testTag(Tags.ShowQr),
                ) {
                    Text("QR code")
                }
            }
            OutlinedButton(
                onClick = { copyAddress(context, runningUrl) },
                modifier = Modifier.fillMaxWidth().testTag(Tags.CopyUrl),
            ) {
                Text("Copy link")
            }
        }

        Text(
            text = shareSummary(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
