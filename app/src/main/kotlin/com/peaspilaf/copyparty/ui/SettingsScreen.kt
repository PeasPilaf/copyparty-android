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
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.core.Validator

/**
 * The handful of knobs that change how the server behaves. They are edited in one form and saved as
 * one change; a value the server could not start with is explained and never written.
 *
 * Expert mode is the one switch that takes effect at once, because it is the door to the advanced
 * tools rather than part of the server's config - and turning it on opens those tools rather than
 * leaving the user to find them.
 */
@Composable
internal fun SettingsScreen(
    viewModel: AppViewModel,
    state: AppState,
    onOpenExpert: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val initial = state.settings
    var port by remember(initial) { mutableStateOf(initial.port.toString()) }
    var lanAccess by remember(initial) { mutableStateOf(initial.lanAccess) }
    var startOnBoot by remember(initial) { mutableStateOf(initial.startOnBoot) }
    var serverName by remember(initial) { mutableStateOf(initial.serverName) }
    var theme by remember(initial) { mutableStateOf(initial.theme) }
    var errors by remember(initial) { mutableStateOf(emptyList<String>()) }

    fun save() {
        // Copy from the saved settings so the expert-only values (raw args, expert mode) survive a
        // save of this form instead of being silently reset to their defaults.
        val edited = state.settings.copy(
            port = port.trim().toIntOrNull() ?: 0,
            lanAccess = lanAccess,
            startOnBoot = startOnBoot,
            serverName = serverName.trim(),
            theme = theme,
        )
        val problems = Validator.validate(state.copy(settings = edited))
            .filter { it.severity == Severity.ERROR && it.path.startsWith("settings") }
        if (problems.isEmpty()) {
            errors = emptyList()
            viewModel.updateSettings(edited)
        } else {
            errors = problems.map { it.message }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(Tags.ScreenSettings),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "How the file server behaves. Changes apply the next time it starts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(Tags.SettingsPort),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Local network", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Other devices on your Wi-Fi can reach the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = lanAccess,
                onCheckedChange = { lanAccess = it },
                modifier = Modifier.testTag(Tags.SettingsLanAccess),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Start on boot", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Start serving as soon as the phone finishes starting up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = startOnBoot,
                onCheckedChange = { startOnBoot = it },
                modifier = Modifier.testTag(Tags.SettingsStartOnBoot),
            )
        }

        OutlinedTextField(
            value = serverName,
            onValueChange = { serverName = it },
            label = { Text("Server name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(Tags.SettingsServerName),
        )

        Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in ThemeMode.values()) {
                OutlinedButton(
                    onClick = { theme = option },
                    modifier = Modifier.testTag(Tags.themeOption(option)),
                ) {
                    Text(themeLabel(option))
                }
            }
        }

        if (errors.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().testTag(Tags.SettingsErrors)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Not saved",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    for (message in errors) {
                        Text(
                            text = "\u2022 $message",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().testTag(Tags.SettingsSave),
        ) {
            Text("Save settings")
        }

        Text(text = "Expert mode", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Show the expert tools", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "The raw copyparty config, global arguments and per-folder " +
                        "advanced flags, and the full copyparty web UI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.settings.expertMode,
                onCheckedChange = { on ->
                    // Saving immediately is the point: the switch is a door, not a server setting.
                    viewModel.updateSettings(state.settings.copy(expertMode = on))
                    if (on) onOpenExpert()
                },
                modifier = Modifier.testTag(Tags.ExpertToggle),
            )
        }
        if (state.settings.expertMode) {
            OutlinedButton(
                onClick = onOpenExpert,
                modifier = Modifier.fillMaxWidth().testTag(Tags.SettingsExpert),
            ) {
                Text("Expert tools")
            }
        }

        OutlinedButton(
            onClick = onOpenLog,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Server log")
        }
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
