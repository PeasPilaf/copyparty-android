package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.server.ServerStatus

/**
 * App shell. The real screens live in their own files; this file only wires navigation and the
 * server state that every screen observes.
 */
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val status by viewModel.serverStatus.collectAsState()
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Copyparty", style = MaterialTheme.typography.headlineMedium)
            Text(statusLabel(status), modifier = Modifier.testTag("server-status"))
            Button(onClick = { viewModel.startServer() }, modifier = Modifier.testTag("start-server")) {
                Text("Start server")
            }
            Button(onClick = { viewModel.stopServer() }, modifier = Modifier.testTag("stop-server")) {
                Text("Stop server")
            }
        }
    }
}

fun statusLabel(status: ServerStatus): String = when (status) {
    is ServerStatus.Stopped -> "Server stopped"
    is ServerStatus.Starting -> "Starting\u2026"
    is ServerStatus.Stopping -> "Stopping\u2026"
    is ServerStatus.Running -> "Running at ${status.urls.firstOrNull().orEmpty()}"
    is ServerStatus.Failed -> "Failed: ${status.message}"
}
