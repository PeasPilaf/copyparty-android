package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.server.serverLogTail
import kotlinx.coroutines.launch

/**
 * The end of the log copyparty itself writes.
 *
 * A server that will not start has to be diagnosable without a laptop and a USB cable, so this is
 * the same file the embedded server logs to (`<filesDir>/copyparty/copyparty.log`). A missing log is
 * not an error: on a phone that has never started the server, "nothing has been logged yet" is the
 * truth.
 */
@Composable
internal fun ServerLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val log = remember { serverLogTail(context.filesDir) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Server log",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = "The last lines of the file the embedded server writes. The end of the log is " +
                    "where a failed start explains itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (log.isBlank()) {
                Text(
                    text = "Nothing has been logged yet. Start the server once and come back.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                OutlinedButton(
                    onClick = {
                        copyAddress(context, log, label = "copyparty log")
                        scope.launch { snackbar.showSnackbar("Log copied") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy log")
                }
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
