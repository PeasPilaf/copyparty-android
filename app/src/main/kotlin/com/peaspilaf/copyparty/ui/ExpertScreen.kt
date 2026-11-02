package com.peaspilaf.copyparty.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.Validator
import com.peaspilaf.copyparty.server.ServerStatus

/** The testTags of the expert tools, shared with `ExpertScreenTest`. */
internal object ExpertTags {
    const val Mode = "expert-mode"
    const val Config = "expert-config"
    const val Copy = "expert-copy"
    const val Args = "expert-args"
    const val SaveArgs = "expert-save-args"
    const val Volumes = "expert-volumes"
    const val ExtraFlags = "expert-extra-flags"
    const val SaveFlags = "expert-save-flags"
    const val OpenWebUi = "expert-open-webui"
    const val Back = "expert-back"
}

/**
 * The power user's screen: the real copyparty, not a simplified copy of it.
 *
 * It shows the `copyparty.conf` the app writes from the saved state (copyable, shareable), lets the
 * user append raw global directives and per-folder volflags, and opens copyparty's own web UI - which
 * has its own control panel and therefore everything copyparty can do. Nothing here is required for
 * the simple flow, and nothing the simple flow can do is removed here.
 *
 * The one thing this screen does not show is the log path the app adds when it starts the server, so
 * a copied config still runs outside the app; the caption says so instead of claiming the copy is
 * byte-identical to the file on disk.
 */
@Composable
fun ExpertScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.serverStatus.collectAsState()
    val pendingApply by viewModel.pendingApply.collectAsState()
    val context = LocalContext.current

    var openUrl by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val url = openUrl
    if (url != null) {
        // One back step: the web UI was opened from here, so leaving it returns to these tools
        // instead of closing the whole expert route in one jump.
        BackHandler { openUrl = null }
        // copyparty's own UI at the server root: the escape hatch for everything this screen cannot
        // express in a form.
        BrowserScreen(url = url, onBack = { openUrl = null })
        return
    }

    val rendered = ConfigRenderer.render(state)
    val serverUrl = (status as? ServerStatus.Running)?.urls?.firstOrNull()

    var argsDraft by remember(state.settings.extraArgs) { mutableStateOf(state.settings.extraArgs) }
    val argsIssue = Validator.validate(state.copy(settings = state.settings.copy(extraArgs = argsDraft)))
        .firstOrNull { it.path == "settings.extraArgs" && it.severity == Severity.ERROR }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Full-screen route above the tab shell, on an edge-to-edge activity: without this the
            // back arrow is under the status bar and the last card under the navigation bar.
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(ExpertTags.Back)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Expert tools",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Switch(
                checked = state.settings.expertMode,
                onCheckedChange = { on -> viewModel.updateSettings(state.settings.copy(expertMode = on)) },
                modifier = Modifier.testTag(ExpertTags.Mode),
            )
        }
        Text(
            text = "Expert mode hands you the real copyparty. Normal use never needs anything on this " +
                "screen, and nothing you set here changes the simple screens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Saving an argument or a folder flag changes the config file, not the server that is already
        // running with the old one. Saying so - and offering the restart that actually happens - is
        // the difference between an honest screen and a "saved" that changed nothing.
        if (pendingApply && serverUrl != null) {
            RestartToApplyBanner(onRestartToApply = { viewModel.applyIfSupported() })
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "copyparty.conf", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Rendered from your saved settings. The app adds its own log path when it " +
                        "starts the server, so a copy of this file runs anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = rendered,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().testTag(ExpertTags.Config),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            if (clipboard == null) {
                                note = "This phone has no clipboard."
                            } else {
                                clipboard.setPrimaryClip(ClipData.newPlainText("copyparty.conf", rendered))
                                note = "copyparty.conf copied to the clipboard."
                            }
                        },
                        modifier = Modifier.testTag(ExpertTags.Copy),
                    ) {
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "copyparty.conf")
                                putExtra(Intent.EXTRA_TEXT, rendered)
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, "Share copyparty.conf")) }
                                .onFailure { note = "No app on this phone can share the config." }
                        },
                    ) {
                        Text("Share")
                    }
                }
                // Confirm right under the buttons: the screen is long, and a message at the bottom
                // would be a confirmation nobody sees.
                val message = note
                if (message != null) {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Extra global arguments", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "One copyparty directive per line, added under [global]. They are copied into " +
                        "the file as written, so a line that starts a [section] is refused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = argsDraft,
                    onValueChange = { argsDraft = it },
                    label = { Text("e.g. --dedup") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag(ExpertTags.Args),
                )
                val issue = argsIssue
                if (issue != null) {
                    Text(
                        text = issue.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { viewModel.updateSettings(state.settings.copy(extraArgs = argsDraft)) },
                    enabled = issue == null,
                    modifier = Modifier.testTag(ExpertTags.SaveArgs),
                ) {
                    Text("Save arguments")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "The full copyparty web UI", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "The web UI and its control panel expose everything copyparty can do: uploads, " +
                        "the text editor, thumbnails, search, per-folder settings, the server log and " +
                        "copyparty's admin panel. The control panel is part of that page, so it appears " +
                        "once you open the UI as an account with admin permission.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { openUrl = serverUrl },
                    enabled = serverUrl != null,
                    modifier = Modifier.testTag(ExpertTags.OpenWebUi),
                ) {
                    Text("Open full copyparty UI")
                }
                if (serverUrl == null) {
                    Text(
                        text = "Start the server to open the full copyparty UI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = "Folders",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(ExpertTags.Volumes),
        )
        Text(
            text = "Advanced flags for one folder. They are merged into that folder's flags line, after " +
                "the e2d upload flag when it has one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.shares.forEachIndexed { index, share ->
            key(share.id) {
                ShareFlagsCard(
                    state = state,
                    index = index,
                    share = share,
                    onSave = { flags -> viewModel.saveShare(share.copy(extraVolFlags = flags)) },
                )
            }
        }
        if (state.shares.isEmpty()) {
            Text(
                text = "No folders yet. Add one on the Shares tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    }
}

/** One folder's raw volflags. Saving edits a copy of the folder, so grants and path are untouched. */
@Composable
private fun ShareFlagsCard(
    state: AppState,
    index: Int,
    share: Share,
    onSave: (String) -> Unit,
) {
    var draft by remember(share.id, share.extraVolFlags) { mutableStateOf(share.extraVolFlags) }
    val candidate = state.copy(
        shares = state.shares.map { if (it.id == share.id) it.copy(extraVolFlags = draft) else it },
    )
    val issue = Validator.validate(candidate)
        .firstOrNull { it.path == "shares[$index].extraVolFlags" && it.severity == Severity.ERROR }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = share.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = ConfigRenderer.mountPath(share.urlPath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("e.g. nodupe, nosub") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(ExpertTags.ExtraFlags),
            )
            val problem = issue
            if (problem != null) {
                Text(
                    text = problem.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { onSave(draft) },
                enabled = problem == null,
                modifier = Modifier.testTag(ExpertTags.SaveFlags),
            ) {
                Text("Save folder flags")
            }
        }
    }
}
