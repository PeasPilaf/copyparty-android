package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.peaspilaf.copyparty.data.StorageAccess
import java.io.File

/**
 * The in-app fallback when no system Settings screen could be opened. Tagged so the journey tests
 * can tell "the app explained the manual steps" from "the tap silently did nothing".
 */
internal const val StorageGrantHelp = "storage-grant-help"

/** Dismisses [StorageGrantHelp]. */
internal const val StorageGrantHelpDismiss = "storage-grant-help-dismiss"

/**
 * Picks the folder a share is rooted in.
 *
 * It starts at a chooser of places instead of one hardcoded folder: the app's own folder (always
 * there, no permission needed) and, on the self-hosted build once All files access is granted, the
 * phone's shared storage. The access state is re-read every time the screen resumes, so granting in
 * system settings is reflected when the user comes back - no restart, no stuck picker.
 *
 * An empty folder and a folder the app cannot read are different things, and the screen says which
 * one it is: "No subfolders here" is not an error, but "This folder cannot be opened" is, and it
 * keeps the route back to granting access on screen.
 */
@Composable
internal fun FolderPickerScreen(
    storage: StorageAccess,
    onSelect: (File) -> Unit,
    onCancel: () -> Unit,
) {
    var accessGranted by remember(storage) { mutableStateOf(storage.hasAllFilesAccess()) }
    var roots by remember(storage) { mutableStateOf(storage.browseRoots()) }
    // Shown only when the system refused every route to its own All-files-access screen.
    var grantHelp by remember(storage) { mutableStateOf(false) }

    // Coming back from the system "All files access" screen must change the picker immediately.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = storage.hasAllFilesAccess()
        accessGranted = granted
        if (granted) grantHelp = false
        roots = storage.browseRoots()
    }

    val appRoot = remember(storage) { storage.appShareRoot() }
    var rootPath by remember(storage) { mutableStateOf<String?>(null) }
    var currentPath by remember(storage) { mutableStateOf<String?>(null) }

    val selectedRoot = rootPath?.let { File(it) }
    val current = currentPath?.let { File(it) }
    val listing = current?.let { folder -> runCatching { storage.listFolders(folder) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(Tags.FolderPicker),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Choose a folder", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "The share starts in this folder. Pick a subfolder to narrow it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!storage.supportsExternalFolders) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Only the app's folder",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "This build can share only the app's own folder. The self-hosted " +
                            "build can share any folder.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Use Add files on Home to copy documents into it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (storage.supportsExternalFolders && !accessGranted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Share folders on this phone",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Allow access to all files once and this picker can open any folder " +
                            "on the phone, in place - the files are not copied.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            // Never a bare startActivity: some OEM Settings builds have no
                            // app-specific All-files-access page, and a throw used to be
                            // swallowed, leaving the tap with no visible effect.
                            if (!storage.openAllFilesAccess()) grantHelp = true
                        },
                        modifier = Modifier.testTag(Tags.StorageGrant),
                    ) {
                        Text("Allow access to all files")
                    }
                }
            }
        }

        if (selectedRoot == null || current == null) {
            RootChooser(
                roots = roots,
                appRoot = appRoot,
                onOpenRoot = { root ->
                    rootPath = root.absolutePath
                    currentPath = root.absolutePath
                },
            )
        } else {
            Breadcrumb(root = selectedRoot, current = current, appRoot = appRoot)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onSelect(current) },
                    modifier = Modifier.weight(1f).testTag(Tags.PickerSelect),
                ) {
                    Text("Use this folder")
                }
                OutlinedButton(
                    onClick = {
                        if (current.absolutePath == selectedRoot.absolutePath) {
                            rootPath = null
                            currentPath = null
                        } else {
                            currentPath = current.parentFile?.absolutePath ?: selectedRoot.absolutePath
                        }
                    },
                    modifier = Modifier.weight(1f).testTag(Tags.PickerUp),
                ) {
                    Text("Up")
                }
            }

            Text(text = "Subfolders", style = MaterialTheme.typography.titleMedium)
            val folders = listing?.getOrNull()
            val unreadable = listing?.isFailure == true
            when {
                unreadable -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "This folder cannot be opened.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(Tags.PickerUnreadable),
                    )
                    Text(
                        text = if (storage.supportsExternalFolders && !accessGranted) {
                            "Allow access to all files above, then come back to this folder."
                        } else {
                            "The app does not have permission to read it. Pick another folder."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                folders.isNullOrEmpty() -> Text(
                    text = "No subfolders here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(Tags.PickerEmpty),
                )
                else -> for (folder in folders) {
                    OutlinedButton(
                        onClick = { currentPath = folder.absolutePath },
                        modifier = Modifier.fillMaxWidth().testTag(Tags.pickerFolder(folder.name)),
                    ) {
                        Text(folder.name)
                    }
                }
            }
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }

    if (grantHelp) {
        AlertDialog(
            onDismissRequest = { grantHelp = false },
            title = { Text("Allow access in Settings") },
            text = {
                // The tag lives on the container so tests assert the message, not the dialog chrome.
                Column(
                    modifier = Modifier.testTag(StorageGrantHelp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Android did not open its All files access screen on this phone. " +
                            "You can turn the permission on yourself:",
                    )
                    Text(
                        text = "Settings -> Apps -> Special app access -> All files access -> " +
                            "Copyparty -> Allow",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { grantHelp = false },
                    modifier = Modifier.testTag(StorageGrantHelpDismiss),
                ) {
                    Text("Got it")
                }
            },
        )
    }
}

@Composable
private fun RootChooser(
    roots: List<File>,
    appRoot: File,
    onOpenRoot: (File) -> Unit,
) {
    Text(text = "Where are the files?", style = MaterialTheme.typography.titleMedium)
    for (root in roots) {
        val isApp = root.absolutePath == appRoot.absolutePath
        val label = if (isApp) "App folder" else "This phone"
        val tag = if (isApp) Tags.PickerRootApp else Tags.PickerRootPhone
        OutlinedButton(
            onClick = { onOpenRoot(root) },
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (isApp) {
                        "Always available. Needs no permission."
                    } else {
                        "Internal storage. Every folder on the phone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** "App folder / Photos": the human where-am-I line, not a filesystem path. */
@Composable
private fun Breadcrumb(root: File, current: File, appRoot: File) {
    val rootLabel = if (root.absolutePath == appRoot.absolutePath) "App folder" else "This phone"
    val relative = current.absolutePath
        .removePrefix(root.absolutePath)
        .trim('/')
        .split('/')
        .filter { it.isNotEmpty() }
    val text = (listOf(rootLabel) + relative).joinToString(" / ")
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.testTag(Tags.PickerBreadcrumb),
    )
}
