package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The share editor promise: permissions are per person, "anyone on the network" is one switch, and
 * what you tick is exactly what the generated copyparty config will contain. A share that cannot
 * work (unknown person, unusable link path) says so while you are editing it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ShareEditorTest {

    @get:Rule
    val compose = createComposeRule()

    private val alice = User("alice", "hunter2")

    private val share = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant("alice", emptySet())),
    )

    private fun show(viewModel: AppViewModel) {
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(viewModel)
            }
        }
    }

    private fun openEditor(state: AppState): FakeConfigStore {
        val store = FakeConfigStore(state)
        show(testViewModel(store = store))
        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.ShareEdit)
        compose.onTag(UiTags.ShareEditor).assertIsDisplayed()
        return store
    }

    @Test
    fun `granting alice view access and saving stores exactly that grant`() {
        val store = openEditor(AppState(users = listOf(alice), shares = listOf(share)))

        compose.tap(UiTags.permission("alice", Permission.VIEW))
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().grants)
            .containsExactly(Grant("alice", setOf(Permission.VIEW)))
    }

    @Test
    fun `turning on anyone on the network saves the anonymous grant`() {
        val store = openEditor(
            AppState(users = listOf(alice), shares = listOf(share.copy(grants = emptyList()))),
        )

        compose.onTag(UiTags.AnyoneOnNetwork).assertIsOff()
        compose.tap(UiTags.AnyoneOnNetwork)
        compose.tap(UiTags.ShareEditorSave)

        val everyone = store.state.shares.single().grants.single { it.isEveryone }
        assertThat(everyone.principal).isEqualTo(Grant.EVERYONE)
        assertThat(everyone.permissions).contains(Permission.VIEW)
    }

    @Test
    fun `turning anyone on the network off removes the anonymous grant and keeps alice`() {
        val shareWithEveryone = share.copy(
            grants = listOf(
                Grant(Grant.EVERYONE, setOf(Permission.VIEW)),
                Grant("alice", setOf(Permission.VIEW)),
            ),
        )
        val store = openEditor(AppState(users = listOf(alice), shares = listOf(shareWithEveryone)))

        compose.onTag(UiTags.AnyoneOnNetwork).assertIsOn()
        compose.tap(UiTags.AnyoneOnNetwork)
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().grants)
            .containsExactly(Grant("alice", setOf(Permission.VIEW)))
    }

    @Test
    fun `a grant for an unknown user shows the validator message`() {
        openEditor(
            AppState(
                users = listOf(alice),
                shares = listOf(share.copy(grants = listOf(Grant("ghost", setOf(Permission.VIEW))))),
            ),
        )

        compose.onTag(UiTags.ShareIssues).assertExists()
        compose.assertTextShown("Unknown user")
    }

    @Test
    fun `clearing the link path shows the validator message`() {
        openEditor(AppState(users = listOf(alice), shares = listOf(share)))

        compose.onTag(UiTags.ShareLinkField).performTextClearance()
        compose.waitForIdle()

        compose.assertTextShown("Link path")
    }

    @Test
    fun `picking the app folder creates a share rooted in that folder`() {
        val storage = FakeStorageAccess()
        val store = FakeConfigStore(AppState())
        show(testViewModel(store = store, storage = storage))

        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.AddShare)
        compose.tap(UiTags.FolderSelect)
        compose.onTag(UiTags.ShareEditor).assertIsDisplayed()
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().localPath).isEqualTo(storage.appRoot.absolutePath)
    }
}
