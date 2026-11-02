package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The restart-to-apply promise: a change saved while the server is running is not live yet, the app
 * says so on both screens the change can be made from, and the one action on that banner really
 * restarts the server instead of just hiding the message.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ApplyChangesTest {

    @get:Rule
    val compose = createComposeRule()

    private val url = "http://192.168.1.20:3923"

    private val alice = User("alice", "hunter2")

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant("alice", setOf(Permission.VIEW))),
    )

    private fun editWhileRunning(server: FakeServerRuntime): FakeConfigStore {
        val store = FakeConfigStore(AppState(users = listOf(alice), shares = listOf(photos)))
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store, server = server))
            }
        }
        compose.tap(UiTags.NavShares)
        compose.tapInRow(UiTags.ShareRow, "Photos", UiTags.ShareEdit)
        compose.tap(UiTags.permission("alice", Permission.UPLOAD))
        compose.tap(UiTags.ShareEditorSave)
        return store
    }

    @Test
    fun `editing while running shows a restart-to-apply banner`() {
        editWhileRunning(FakeServerRuntime(ServerStatus.Running(listOf(url), 3923)))

        compose.onTag(UiTags.HomeRestartToApply).assertExists()
        compose.assertTextShown("Restart to apply")

        compose.tap(UiTags.NavHome)
        compose.onTag(UiTags.HomeRestartToApply).assertExists()
    }

    @Test
    fun `the banner restarts the server without claiming the change was live`() {
        val server = FakeServerRuntime(ServerStatus.Running(listOf(url), 3923))
        editWhileRunning(server)

        // Saved, but not live: the app asks for a restart instead of pretending otherwise.
        compose.assertTextShown("Restart to apply")
        assertThat(server.applyCalls).isEqualTo(0)

        compose.tap(UiTags.HomeRestartToApply)

        assertThat(server.applyCalls).isEqualTo(1)
        compose.waitForIdle()
        compose.onTag(UiTags.HomeRestartToApply).assertDoesNotExist()
    }
}
