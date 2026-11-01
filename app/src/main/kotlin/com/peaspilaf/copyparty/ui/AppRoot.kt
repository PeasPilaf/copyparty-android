package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.server.ServerStatus

/**
 * The app shell: four tabs along the bottom, and the folder picker / share editor as one step
 * inside the Shares tab. Every screen observes the same [AppViewModel], so a change made on one tab
 * is visible on the others straight away.
 */
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(AppTab.Home) }
    var sharesRoute by remember { mutableStateOf<SharesRoute>(SharesRoute.ShowList) }

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
                AppTab.Home -> HomeScreen(viewModel, state)
                AppTab.Shares -> when (val route = sharesRoute) {
                    SharesRoute.ShowList -> SharesScreen(
                        shares = state.shares,
                        onAdd = { sharesRoute = SharesRoute.ShowPicker },
                        onEdit = { sharesRoute = SharesRoute.ShowEditor(it) },
                        onEnabledChange = { id, enabled -> viewModel.setShareEnabled(id, enabled) },
                    )
                    SharesRoute.ShowPicker -> FolderPickerScreen(
                        storage = viewModel.storage,
                        onSelect = { folder ->
                            sharesRoute = SharesRoute.ShowEditor(viewModel.newShare(folder.absolutePath))
                        },
                        onCancel = { sharesRoute = SharesRoute.ShowList },
                    )
                    is SharesRoute.ShowEditor -> ShareEditorScreen(
                        viewModel = viewModel,
                        state = state,
                        share = route.share,
                        onClose = { sharesRoute = SharesRoute.ShowList },
                    )
                }
                AppTab.People -> PeopleScreen(viewModel, state)
                AppTab.Settings -> SettingsScreen(viewModel, state)
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

/** Where the Shares tab is: the list, the folder picker, or one share's editor. */
private sealed interface SharesRoute {
    data object ShowList : SharesRoute
    data object ShowPicker : SharesRoute
    data class ShowEditor(val share: Share) : SharesRoute
}

/** The one-line server state shown on Home. */
fun statusLabel(status: ServerStatus): String = when (status) {
    is ServerStatus.Stopped -> "Server stopped"
    is ServerStatus.Starting -> "Starting\u2026"
    is ServerStatus.Stopping -> "Stopping\u2026"
    is ServerStatus.Running -> "Server running"
    is ServerStatus.Failed -> "Could not start"
}
