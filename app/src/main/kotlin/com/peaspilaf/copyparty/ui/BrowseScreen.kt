package com.peaspilaf.copyparty.ui

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.peaspilaf.copyparty.browse.BrowseEntry
import com.peaspilaf.copyparty.browse.BrowseFilePicker
import com.peaspilaf.copyparty.browse.CopypartyApi
import com.peaspilaf.copyparty.browse.CopypartyClient
import com.peaspilaf.copyparty.browse.CopypartyException
import com.peaspilaf.copyparty.browse.DownloadSaver
import com.peaspilaf.copyparty.browse.SavedDownload
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The testTags of the native file screen, kept here so the screen cannot rename a control without
 * the tests in `BrowseScreenTest` / `BrowseFilesTest` failing.
 */
internal object BrowseTags {
    const val List = "browse-list"
    const val Row = "browse-row"
    const val Breadcrumb = "browse-breadcrumb"
    const val Back = "browse-back"
    const val Up = "browse-up"
    const val Upload = "browse-upload"
    const val Download = "browse-download"
    const val AuthField = "browse-auth-field"
    const val AuthSubmit = "browse-auth-submit"
    const val Empty = "browse-empty"
    const val Error = "browse-error"
    const val Retry = "browse-retry"
    const val Saved = "browse-saved"
    const val Open = "browse-open"
    const val Share = "browse-share"
}

/**
 * The folder this phone is serving, on the phone. This is the owner's own view of a share: folders
 * open on tap, files download to a place the user can find, and a folder that needs a password asks
 * for it inline instead of failing silently.
 *
 * [baseUrl] is the server root (the address Home shows); the share's mount path is appended here, so
 * a caller never has to know how copyparty maps volumes. All paths handed to [api] are volume
 * relative: `""` is the share's root.
 */
@Composable
fun BrowseScreen(
    share: Share,
    baseUrl: String,
    onBack: () -> Unit,
    api: CopypartyApi = CopypartyClient(),
    saver: DownloadSaver = rememberDownloadSaver(),
    picker: BrowseFilePicker = rememberBrowseFilePicker(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val volumeUrl = baseUrl.trimEnd('/') + ConfigRenderer.mountPath(share.urlPath)

    var path by remember(share.id) { mutableStateOf("") }
    var passwordDraft by remember(share.id) { mutableStateOf("") }
    var password by remember(share.id) { mutableStateOf<String?>(null) }
    var needsPassword by remember(share.id) { mutableStateOf(false) }
    var authFailed by remember(share.id) { mutableStateOf(false) }
    var entries by remember(share.id) { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var loading by remember(share.id) { mutableStateOf(true) }
    var loadError by remember(share.id) { mutableStateOf<String?>(null) }
    var actionError by remember(share.id) { mutableStateOf<String?>(null) }
    var failedDownload by remember(share.id) { mutableStateOf<String?>(null) }
    var saved by remember(share.id) { mutableStateOf<SavedDownload?>(null) }
    var note by remember(share.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    val canUpload = share.grants.any { Permission.UPLOAD in it.permissions }
    val canRead = share.grants.any {
        Permission.VIEW in it.permissions || Permission.DOWNLOAD in it.permissions
    }

    // One listing per folder, password and explicit refresh. Cancelling the effect on navigation
    // discards the answer of a folder the user already left.
    LaunchedEffect(volumeUrl, path, password, reload) {
        loading = true
        loadError = null
        val result = resultOf {
            api.list(volumeUrl, path, password)
        }
        loading = false
        result.onSuccess {
            entries = it
            needsPassword = false
            authFailed = false
        }.onFailure { error ->
            if (error is CopypartyException.Unauthorized) {
                needsPassword = true
                authFailed = password != null
                entries = emptyList()
            } else {
                loadError = messageOf(error)
            }
        }
    }

    // Back is a ladder: one folder up while a folder is open, and only the root leaves the screen.
    BackHandler(enabled = true) {
        if (path.isEmpty()) onBack() else path = parentPath(path)
    }

    fun download(entryName: String, target: String) {
        scope.launch {
            saved = null
            note = null
            actionError = null
            val result = resultOf {
                val tempDir = File(context.cacheDir, "downloads").apply { mkdirs() }
                val sink = File(tempDir, localFileName(entryName))
                api.download(volumeUrl, target, password, sink)
                saver.save(entryName, sink)
            }
            result.onSuccess {
                saved = it
                failedDownload = null
            }.onFailure { error ->
                if (error is CopypartyException.Unauthorized) needsPassword = true
                actionError = messageOf(error)
                // Remembering which file failed is what makes Retry re-run the download instead of
                // reloading the folder.
                failedDownload = target
            }
        }
    }

    fun upload(file: File) {
        scope.launch {
            actionError = null
            saved = null
            note = null
            val target = joinPath(path, localFileName(file.name))
            val result = resultOf {
                api.upload(volumeUrl, file, target, password)
            }
            result.onSuccess {
                reload += 1
            }.onFailure { error ->
                if (error is CopypartyException.Unauthorized) needsPassword = true
                actionError = "Could not upload ${file.name}: ${messageOf(error)}"
            }
        }
    }

    fun retry() {
        val failed = failedDownload
        if (failed != null) {
            download(failed.substringAfterLast('/'), failed)
        } else {
            reload += 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // This is a full-screen route above the tab shell, and the activity draws edge to edge:
            // without this the back button sits under the status bar and the last row under the
            // navigation bar.
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Back is the same ladder as the system back button: one folder up while a folder is
            // open, and only the root leaves the screen. A back arrow that always leaves would make
            // "go up" a separate, hidden gesture.
            IconButton(
                onClick = { if (path.isEmpty()) onBack() else path = parentPath(path) },
                modifier = Modifier.testTag(BrowseTags.Back),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = share.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (path.isNotEmpty()) {
                IconButton(onClick = { path = parentPath(path) }, modifier = Modifier.testTag(BrowseTags.Up)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Up one folder")
                }
            }
            if (canUpload) {
                IconButton(
                    onClick = { picker.pick { file -> upload(file) } },
                    modifier = Modifier.testTag(BrowseTags.Upload),
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = "Upload a file here")
                }
            }
        }

        Text(
            text = "/" + path,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(BrowseTags.Breadcrumb),
        )
        HorizontalDivider()

        when {
            needsPassword -> PasswordCard(
                password = passwordDraft,
                failed = authFailed,
                onPasswordChange = { passwordDraft = it },
                onUnlock = {
                    password = passwordDraft
                    needsPassword = false
                    // Claim the loading state now, so the frame before the retry runs does not show
                    // "This folder is empty." to someone who just typed the right password.
                    loading = true
                    reload += 1
                },
            )

            loadError != null -> ErrorCard(
                message = loadError.orEmpty(),
                onRetry = { retry() },
            )

            loading -> Text(
                text = "Loading\u2026",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )

            entries.isEmpty() -> Text(
                text = "This folder is empty.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp).testTag(BrowseTags.Empty),
            )

            else -> Column(modifier = Modifier.fillMaxWidth().testTag(BrowseTags.List)) {
                for (entry in sorted(entries)) {
                    val target = joinPath(path, entry.name)
                    // A tap always does something: a folder opens, a file downloads. A row that is
                    // inert is the "nothing happened" bug this screen exists to remove.
                    val tap = when {
                        entry.isFolder -> { { path = target } }
                        canRead -> { { download(entry.name, target) } }
                        else -> null
                    }
                    EntryRow(
                        entry = entry,
                        showDownload = !entry.isFolder && canRead,
                        onTap = tap,
                        onDownload = { download(entry.name, target) },
                    )
                }
            }
        }

        if (actionError != null) {
            ErrorCard(message = actionError.orEmpty(), onRetry = { retry() })
        }

        val done = saved
        if (done != null) {
            SavedCard(
                saved = done,
                onOpen = {
                    val mime = context.contentResolver.getType(done.uri) ?: "*/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(done.uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { note = "No app on this phone can open that file." }
                },
                onShare = {
                    val mime = context.contentResolver.getType(done.uri) ?: "*/*"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, done.uri)
                        clipData = ClipData.newRawUri("file", done.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "Share file")) }
                        .onFailure { note = "No app on this phone can share that file." }
                },
            )
        }

        val message = note
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: BrowseEntry,
    showDownload: Boolean,
    onTap: (() -> Unit)?,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(vertical = 8.dp)
            .testTag(BrowseTags.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.isFolder) "Folder" else humanSize(entry.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDownload) {
            IconButton(onClick = onDownload, modifier = Modifier.testTag(BrowseTags.Download)) {
                Icon(Icons.Filled.Download, contentDescription = "Download ${entry.name}")
            }
        }
    }
}

@Composable
private fun PasswordCard(
    password: String,
    failed: Boolean,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "This folder needs a password", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Password from the share owner",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(BrowseTags.AuthField),
            )
            if (failed) {
                Text(
                    text = "That password did not work. Ask the share owner for it again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onUnlock,
                enabled = password.isNotEmpty(),
                modifier = Modifier.testTag(BrowseTags.AuthSubmit),
            ) {
                Text("Unlock")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(BrowseTags.Error)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry, modifier = Modifier.testTag(BrowseTags.Retry)) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun SavedCard(saved: SavedDownload, onOpen: () -> Unit, onShare: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(BrowseTags.Saved)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Saved: ${saved.location}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen, modifier = Modifier.testTag(BrowseTags.Open)) {
                    Text("Open")
                }
                TextButton(onClick = onShare, modifier = Modifier.testTag(BrowseTags.Share)) {
                    Text("Share")
                }
            }
        }
    }
}

// -- helpers ---------------------------------------------------------------------------------

/** Folders first, then by name: the order a file browser shows, whatever order the server listed. */
private fun sorted(entries: List<BrowseEntry>): List<BrowseEntry> =
    entries.sortedWith(compareByDescending<BrowseEntry> { it.isFolder }.thenBy { it.name.lowercase() })

private fun parentPath(path: String): String = path.substringBeforeLast('/', "")

private fun joinPath(folder: String, name: String): String = if (folder.isEmpty()) name else "$folder/$name"

/** A server-provided name is not a path: keep it inside the temp folder it is written to. */
private fun localFileName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
    return if (base.isEmpty() || base == "." || base == "..") "download" else base
}

private fun messageOf(error: Throwable): String =
    error.message ?: error.javaClass.simpleName

/** Runs a blocking call off the main thread; a cancellation is not an error the screen shows. */
private suspend fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.success(withContext(Dispatchers.IO) { block() })
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

// -- the production seams --------------------------------------------------------------------

/**
 * Downloads land in the phone's Downloads collection on Android 10+ (no permission needed) and in
 * the app's own Downloads folder below that. Both are opened and shared through a content URI.
 */
@Composable
internal fun rememberDownloadSaver(): DownloadSaver {
    val context = LocalContext.current
    return remember(context) {
        DownloadSaver { fileName, source -> saveDownload(context, fileName, source) }
    }
}

private fun saveDownload(context: Context, fileName: String, source: File): SavedDownload =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveToMediaStore(context, fileName, source)
    } else {
        saveToAppDownloads(context, fileName, source)
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToMediaStore(context: Context, fileName: String, source: File): SavedDownload {
    val resolver = context.contentResolver
    val name = localFileName(fileName)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(name))
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: return saveToAppDownloads(context, fileName, source)
    try {
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not open the Downloads collection")
        val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, publish, null, null)
    } catch (error: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        throw error
    }
    return SavedDownload("Downloads/$name", uri)
}

private fun saveToAppDownloads(context: Context, fileName: String, source: File): SavedDownload {
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    directory.mkdirs()
    val target = uniqueFile(directory, localFileName(fileName))
    source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
    val uri = FileProvider.getUriForFile(context, authority(context), target)
    return SavedDownload("Downloads/${target.name}", uri)
}

private fun authority(context: Context): String = "${context.packageName}.files"

private fun uniqueFile(directory: File, name: String): File {
    var candidate = File(directory, name)
    if (!candidate.exists()) return candidate
    val stem = name.substringBeforeLast('.', name)
    val extension = name.substringAfterLast('.', "")
    var counter = 1
    while (candidate.exists()) {
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        candidate = File(directory, "$stem ($counter)$suffix")
        counter += 1
    }
    return candidate
}

private fun mimeTypeOf(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isEmpty()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}

/**
 * The system document picker, copied to a temp file so the upload path only deals with a [File].
 * The picker's content URI is only readable while it is granted, which is why the copy happens here
 * instead of at PUT time.
 */
@Composable
internal fun rememberBrowseFilePicker(): BrowseFilePicker {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((File) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pending
        pending = null
        if (uri != null && callback != null) {
            val picked = runCatching { copyToCache(context, uri) }.getOrNull()
            if (picked != null) callback(picked)
        }
    }
    return remember(launcher) {
        BrowseFilePicker { onPicked ->
            pending = onPicked
            launcher.launch(arrayOf("*/*"))
        }
    }
}

private fun copyToCache(context: Context, uri: Uri): File {
    val name = displayName(context, uri) ?: "upload.bin"
    val directory = File(context.cacheDir, "uploads").apply { mkdirs() }
    val target = File(directory, localFileName(name))
    val opened = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("The chosen file could not be opened")
    opened.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
    return target
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()?.takeIf { it.isNotBlank() }
