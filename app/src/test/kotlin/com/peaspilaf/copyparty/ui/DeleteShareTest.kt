package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The delete-a-share promise: a share can be removed, not only switched off; the confirmation says
 * which folder it is and that the files themselves are untouched; and removing the last share puts
 * the empty state back instead of leaving a screen with nothing to do on it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DeleteShareTest {

    @get:Rule
    val compose = createComposeRule()

    private val alice = User("alice", "hunter2")

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant("alice", setOf(Permission.VIEW))),
    )

    private val docs = Share(
        id = "s2",
        name = "Docs",
        urlPath = "docs",
        localPath = "/storage/docs",
        grants = listOf(Grant("alice", setOf(Permission.VIEW))),
    )

    private fun show(state: AppState): FakeConfigStore {
        val store = FakeConfigStore(state)
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store))
            }
        }
        compose.tap(UiTags.NavShares)
        return store
    }

    @Test
    fun `deleting a share removes it from the store and the list`() {
        val store = show(AppState(users = listOf(alice), shares = listOf(photos, docs)))

        compose.tapInRow(UiTags.ShareRow, "Photos", UiTags.ShareEdit)
        compose.onTag(UiTags.ShareEditor).assertExists()

        compose.tap(UiTags.EditorDelete)
        compose.tap(UiTags.EditorDeleteConfirm)

        assertThat(store.state.shares.map { it.id }).containsExactly("s2")
        compose.onTag(UiTags.ShareEditor).assertDoesNotExist()
        compose.onAllTags(UiTags.ShareRow).assertCountEquals(1)
        compose.onTagInRow(UiTags.ShareRow, "Docs", UiTags.ShareEdit).assertExists()
    }

    @Test
    fun `the confirmation names the folder and says the files are kept`() {
        show(AppState(users = listOf(alice), shares = listOf(photos)))

        compose.tapInRow(UiTags.ShareRow, "Photos", UiTags.ShareEdit)
        compose.tap(UiTags.EditorDelete)

        compose.onDialogContaining(UiTags.EditorDeleteDialog, "Photos").assertExists()
        compose.onDialogContaining(UiTags.EditorDeleteDialog, "kept").assertExists()
    }

    @Test
    fun `cancelling keeps the share`() {
        val store = show(AppState(users = listOf(alice), shares = listOf(photos)))

        compose.tapInRow(UiTags.ShareRow, "Photos", UiTags.ShareEdit)
        compose.tap(UiTags.EditorDelete)
        compose.tap(UiTags.EditorDeleteCancel)

        assertThat(store.state.shares.map { it.id }).containsExactly("s1")
        compose.onTag(UiTags.ShareEditor).assertExists()
        compose.onAllTags(UiTags.ShareRow).assertCountEquals(1)
    }

    @Test
    fun `deleting the last share brings back the empty state`() {
        val store = show(AppState(users = listOf(alice), shares = listOf(photos)))

        compose.tap(UiTags.ShareDelete)
        compose.tap(UiTags.ShareDeleteConfirm)

        assertThat(store.state.shares).isEmpty()
        compose.assertTextShown("No folders yet")
        compose.onTag(UiTags.EmptyAddShare).assertExists()
    }
}
