package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
 * work (unknown person, unusable link path, nobody allowed in) says so while you are editing it,
 * and the folder a share is rooted in can be seen and changed without losing the grants.
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

    private fun show(store: FakeConfigStore, storage: FakeStorageAccess = FakeStorageAccess()) {
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store, storage = storage))
            }
        }
    }

    private fun openEditor(
        state: AppState,
        storage: FakeStorageAccess = FakeStorageAccess(),
    ): FakeConfigStore {
        val store = FakeConfigStore(state)
        show(store, storage)
        compose.tap(UiTags.NavShares)
        compose.tapInRow(UiTags.ShareRow, state.shares.single().name, UiTags.ShareEdit)
        compose.onTag(UiTags.ShareEditor).assertIsDisplayed()
        return store
    }

    private fun folderShown(path: String) = compose.onNode(
        hasTestTag(UiTags.EditorFolder) and hasText(path, substring = true),
        useUnmergedTree = true,
    )

    @Test
    fun `a draft with an unusable link path is refused instead of saved`() {
        val store = openEditor(AppState(users = listOf(alice), shares = listOf(share)))

        compose.onTag(UiTags.ShareLinkField).performTextClearance()
        compose.tap(UiTags.ShareEditorSave)
        compose.waitForIdle()

        // The store still has the old, valid path and the editor is still open: saving a broken
        // share would give the server a config it cannot start with.
        assertThat(store.state.shares.single().urlPath).isEqualTo("photos")
        compose.onTag(UiTags.ShareEditor).assertIsDisplayed()
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
    fun `the editor shows which folder the share is rooted in`() {
        openEditor(AppState(users = listOf(alice), shares = listOf(share)))

        folderShown("/storage/photos").assertExists()
    }

    @Test
    fun `changing the folder keeps the existing grants`() {
        val storage = FakeStorageAccess()
        val store = openEditor(
            AppState(users = listOf(alice), shares = listOf(share.copy(grants = emptyList()))),
            storage,
        )
        compose.tap(UiTags.permission("alice", Permission.VIEW))

        compose.tap(UiTags.EditorChangeFolder)
        compose.tap(UiTags.PickerRootApp)
        compose.tap(UiTags.PickerSelect)
        folderShown(storage.appRoot.absolutePath).assertExists()
        compose.tap(UiTags.ShareEditorSave)

        val saved = store.state.shares.single()
        assertThat(saved.localPath).isEqualTo(storage.appRoot.absolutePath)
        assertThat(saved.grants).containsExactly(Grant("alice", setOf(Permission.VIEW)))
    }

    @Test
    fun `saving a share that lets nobody in is blocked and explained`() {
        val store = openEditor(
            AppState(users = listOf(alice), shares = listOf(share.copy(grants = emptyList()))),
        )

        compose.tap(UiTags.ShareEditorSave)

        compose.onTag(UiTags.EditorNoAccess).assertExists()
        compose.onTag(UiTags.ShareEditor).assertExists()
        assertThat(store.state.shares.single().grants).isEmpty()
    }

    @Test
    fun `the Anyone can view preset gives everyone read access`() {
        val store = openEditor(
            AppState(users = listOf(alice), shares = listOf(share.copy(grants = emptyList()))),
        )

        compose.tap(UiTags.PresetAnyoneView)
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().grants)
            .containsExactly(Grant(Grant.EVERYONE, setOf(Permission.VIEW)))
    }

    @Test
    fun `the dropbox preset lets anyone upload`() {
        val store = openEditor(
            AppState(users = listOf(alice), shares = listOf(share.copy(grants = emptyList()))),
        )

        compose.tap(UiTags.PresetDropbox)
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().grants)
            .containsExactly(Grant(Grant.EVERYONE, setOf(Permission.UPLOAD)))
    }

    @Test
    fun `choosing only the people I choose drops the anonymous grant`() {
        val shareWithEveryone = share.copy(
            grants = listOf(
                Grant(Grant.EVERYONE, setOf(Permission.VIEW)),
                Grant("alice", setOf(Permission.VIEW)),
            ),
        )
        val store = openEditor(AppState(users = listOf(alice), shares = listOf(shareWithEveryone)))

        compose.tap(UiTags.PresetPeople)
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().grants)
            .containsExactly(Grant("alice", setOf(Permission.VIEW)))
    }

    @Test
    fun `picking the app folder creates a share rooted in that folder`() {
        val storage = FakeStorageAccess()
        val store = FakeConfigStore(AppState())
        show(store, storage)

        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.AddShare)
        compose.tap(UiTags.PickerRootApp)
        compose.tap(UiTags.PickerSelect)
        compose.onTag(UiTags.ShareEditor).assertExists()
        compose.tap(UiTags.PresetAnyoneView)
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().localPath).isEqualTo(storage.appRoot.absolutePath)
    }

    @Test
    fun `a folder picked from the picker is a share anyone on the network can open straight away`() {
        // A novice picks a folder, taps Save and has a working link: the share must not be saved with
        // nobody granted, because then it would be invisible on the server (the shipped bug).
        val storage = FakeStorageAccess()
        val store = FakeConfigStore(AppState())
        show(store, storage)

        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.AddShare)
        compose.tap(UiTags.PickerRootApp)
        compose.tap(UiTags.PickerSelect)
        compose.tap(UiTags.ShareEditorSave)

        val saved = store.state.shares.single()
        assertThat(saved.localPath).isEqualTo(storage.appRoot.absolutePath)
        assertThat(saved.grants).containsExactly(Grant(Grant.EVERYONE, setOf(Permission.VIEW)))
    }
}
