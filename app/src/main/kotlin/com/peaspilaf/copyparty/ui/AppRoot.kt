package com.peaspilaf.copyparty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.server.ServerStatus

/**
 * The app shell: four tabs along the bottom, and the folder picker / share editor as one step inside
 * the Shares tab. Every screen observes the same [AppViewModel], so a change made on one tab is
 * visible on the others straight away.
 *
 * The full-screen routes - the QR code, the in-app browser, the native file browser, the server log
 * and the expert tools - sit above the tab shell. All of them are one back step away from where they
 * were opened, and the system back gesture never finishes the activity from inside them.
 */
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.serverStatus.collectAsState()
    val pendingApply by viewModel.pendingApply.collectAsState()
    val runningUrl = (status as? ServerStatus.Running)?.urls?.firstOrNull()

    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var sharesMode by rememberSaveable { mutableStateOf(SharesMode.List) }
    var pickerReturn by rememberSaveable { mutableStateOf(SharesMode.List) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var overlayArg by rememberSaveable { mutableStateOf("") }
    var editorDraft by remember { mutableStateOf<Share?>(null) }

    fun openPicker(returnTo: SharesMode) {
        pickerReturn = returnTo
        sharesMode = SharesMode.Picker
    }

    fun closeEditor() {
        editorDraft = null
        sharesMode = SharesMode.List
    }

    fun openOverlay(next: Overlay, argument: String = "") {
        overlayArg = argument
        overlay = next
    }

    fun openBrowse(share: Share) = openOverlay(Overlay.Browse, share.id)

    // One back step at a time, innermost first. A full-screen route closes; the picker and the
    // editor go back to what opened them; another tab goes Home. Only Home itself leaves the app.
    BackHandler(enabled = overlay != Overlay.None) {
        overlay = Overlay.None
    }
    BackHandler(
        enabled = overlay == Overlay.None && tab == AppTab.Shares && sharesMode != SharesMode.List,
    ) {
        if (sharesMode == SharesMode.Picker) {
            sharesMode = pickerReturn
            if (pickerReturn == SharesMode.List) editorDraft = null
        } else {
            closeEditor()
        }
    }
    BackHandler(
        enabled = overlay == Overlay.None && sharesMode == SharesMode.List && tab != AppTab.Home,
    ) {
        tab = AppTab.Home
    }

    when (overlay) {
        Overlay.Qr -> {
            QrScreen(url = overlayArg, onBack = { overlay = Overlay.None })
            return
        }
        Overlay.Browser -> {
            BrowserScreen(url = overlayArg, onBack = { overlay = Overlay.None })
            return
        }
        Overlay.Log -> {
            ServerLogScreen(onBack = { overlay = Overlay.None })
            return
        }
        Overlay.Expert -> {
            ExpertScreen(viewModel = viewModel, onBack = { overlay = Overlay.None })
            return
        }
        Overlay.Browse -> {
            val share = state.shares.firstOrNull { it.id == overlayArg }
                ?: state.shares.firstOrNull { it.enabled }
            if (share != null) {
                BrowseScreen(
                    share = share,
                    baseUrl = runningUrl ?: "http://127.0.0.1:${state.settings.port}",
                    onBack = { overlay = Overlay.None },
                )
                return
            }
            // The share vanished while it was open: there is nothing left to browse.
            LaunchedEffect(overlay) { overlay = Overlay.None }
        }
        Overlay.None -> Unit
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                for (candidate in AppTab.values()) {
                    NavigationBarItem(
                        selected = tab == candidate,
                        onClick = { tab = candidate },
                        icon = { Icon(candidate.icon, contentDescription = null) },
                        label = { Text(candidate.label) },
                        modifier = Modifier.testTag(candidate.tag),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.Home -> HomeScreen(
                    viewModel = viewModel,
                    state = state,
                    showRestartToApply = pendingApply && status is ServerStatus.Running,
                    onChooseFolder = {
                        openPicker(SharesMode.List)
                        tab = AppTab.Shares
                    },
                    onBrowseFiles = {
                        state.shares.firstOrNull { it.enabled }?.let { openBrowse(it) }
                    },
                    onShowQr = { url -> openOverlay(Overlay.Qr, url) },
                    onOpenLog = { openOverlay(Overlay.Log) },
                    onOpenExpert = { openOverlay(Overlay.Expert) },
                    onRestartToApply = { viewModel.applyIfSupported() },
                )
                AppTab.Shares -> Box(modifier = Modifier.fillMaxSize()) {
                    // The list stays composed under the picker and the editor, so going one step in
                    // never throws the list away and coming back is instant. The step on top paints
                    // an opaque surface and takes the touches, so it really is a modal step.
                    SharesScreen(
                        state = state,
                        runningUrl = runningUrl,
                        showRestartToApply = pendingApply && status is ServerStatus.Running,
                        onAdd = { openPicker(SharesMode.List) },
                        onEdit = { share ->
                            editorDraft = share
                            sharesMode = SharesMode.Editor
                        },
                        onBrowse = { share -> openBrowse(share) },
                        onEnabledChange = { id, enabled -> viewModel.setShareEnabled(id, enabled) },
                        onDeleteConfirmed = { share -> viewModel.deleteShare(share.id) },
                        onRestartToApply = { viewModel.applyIfSupported() },
                    )
                    when (sharesMode) {
                        SharesMode.List -> Unit
                        SharesMode.Picker -> StepOnTop {
                            FolderPickerScreen(
                                storage = viewModel.storage,
                                onSelect = { folder ->
                                    if (pickerReturn == SharesMode.Editor) {
                                        // Re-rooting a share keeps the grants being edited.
                                        editorDraft = editorDraft?.copy(localPath = folder.absolutePath)
                                    } else {
                                        // Name the share after the folder, so three shares are three
                                        // different rows rather than three "Shared folder"s.
                                        val isAppFolder = folder.absolutePath ==
                                            viewModel.storage.appShareRoot().absolutePath
                                        editorDraft = viewModel.newShare(
                                            localPath = folder.absolutePath,
                                            name = if (isAppFolder) "App folder" else folder.name,
                                        )
                                    }
                                    sharesMode = SharesMode.Editor
                                },
                                onCancel = {
                                    sharesMode = pickerReturn
                                    if (pickerReturn == SharesMode.List) editorDraft = null
                                },
                            )
                        }
                        SharesMode.Editor -> StepOnTop {
                            val draft = editorDraft
                            if (draft != null) {
                                ShareEditorScreen(
                                    viewModel = viewModel,
                                    state = state,
                                    share = draft,
                                    onDraftChange = { editorDraft = it },
                                    onChangeFolder = { openPicker(SharesMode.Editor) },
                                    onClose = { closeEditor() },
                                )
                            } else {
                                LaunchedEffect(sharesMode) { sharesMode = SharesMode.List }
                            }
                        }
                    }
                }
                AppTab.People -> PeopleScreen(viewModel, state)
                AppTab.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    state = state,
                    onOpenExpert = { openOverlay(Overlay.Expert) },
                    onOpenLog = { openOverlay(Overlay.Log) },
                )
            }
        }
    }
}

/** One tab per screen. The tag is the one the UI tests use to reach the tab. */
private enum class AppTab(
    val label: String,
    val tag: String,
    val icon: ImageVector,
) {
    Home("Home", Tags.NavHome, Icons.Filled.Home),
    Shares("Shares", Tags.NavShares, Icons.Filled.Folder),
    People("People", Tags.NavPeople, Icons.Filled.Person),
    Settings("Settings", Tags.NavSettings, Icons.Filled.Settings),
}

/** Full-screen routes that sit above the tab shell. */
private enum class Overlay { None, Qr, Browser, Browse, Log, Expert }

/** Where the Shares tab is: the list, the folder picker, or one share's editor. */
private enum class SharesMode { List, Picker, Editor }

/**
 * One step deeper inside the Shares tab: an opaque, full-screen surface painted over the list.
 *
 * It is a modal step, not a second screen: the list stays composed underneath (so nothing is lost by
 * stepping in), while the barrier stops a tap that lands on empty space in the step from reaching a
 * switch on the list behind it.
 */
@Composable
private fun StepOnTop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        for (change in event.changes) {
                            if (!change.isConsumed) change.consume()
                        }
                    }
                }
            },
    ) {
        content()
    }
}

/** The one-line server state shown on Home. */
fun statusLabel(status: ServerStatus): String = when (status) {
    is ServerStatus.Stopped -> "Server stopped"
    is ServerStatus.Starting -> "Starting\u2026"
    is ServerStatus.Stopping -> "Stopping\u2026"
    is ServerStatus.Running -> "Server running"
    is ServerStatus.Failed -> "Could not start"
}
