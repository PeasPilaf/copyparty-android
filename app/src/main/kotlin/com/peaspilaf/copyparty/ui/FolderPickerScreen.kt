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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.data.StorageAccess
import java.io.File

/**
 * Picks the folder a new share is rooted in. Starts in the app's own share folder (always writable,
 * no permission needed) and can walk into subfolders from there.
 */
@Composable
internal fun FolderPickerScreen(
    storage: StorageAccess,
    onSelect: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val root = remember(storage) { storage.appShareRoot() }
    var current by remember(root) { mutableStateOf(root) }
    val folders = runCatching { storage.listFolders(current) }.getOrDefault(emptyList())
    val up: File? = current.parentFile

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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Folder",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = current.absolutePath, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onSelect(current) },
                modifier = Modifier.weight(1f).testTag(Tags.FolderSelect),
            ) {
                Text("Use this folder")
            }
            if (up != null && current.absolutePath != root.absolutePath) {
                OutlinedButton(
                    onClick = { current = up },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Up")
                }
            }
        }

        Text(text = "Subfolders", style = MaterialTheme.typography.titleMedium)
        if (folders.isEmpty()) {
            Text(
                text = "No subfolders here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (folder in folders) {
            OutlinedButton(
                onClick = { current = folder },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(folder.name)
            }
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
