package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
 * The Shares screen promise: every exposed folder is one readable row - its name, the link people
 * open, who is allowed in, and a switch that takes it off the air without deleting it. Adding a
 * folder starts by choosing the folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SharesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val alice = User("alice", "hunter2")
    private val bob = User("bob", "hunter3")

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
        grants = listOf(Grant("bob", setOf(Permission.UPLOAD))),
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
    fun `each share gets a row with its name and link path`() {
        show(AppState(users = listOf(alice, bob), shares = listOf(photos, docs)))

        compose.onAllTags(UiTags.ShareRow).assertCountEquals(2)
        compose.onNode(hasTestTag(UiTags.ShareName) and hasText("Photos"), useUnmergedTree = true)
            .assertExists()
        compose.onNode(
            hasTestTag(UiTags.ShareUrl) and hasText("/photos", substring = true),
            useUnmergedTree = true,
        ).assertExists()
        compose.onNode(hasTestTag(UiTags.ShareName) and hasText("Docs"), useUnmergedTree = true)
            .assertExists()
        compose.onNode(
            hasTestTag(UiTags.ShareUrl) and hasText("/docs", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `the access summary names the people who can open the share`() {
        show(
            AppState(
                users = listOf(alice, bob),
                shares = listOf(
                    photos.copy(
                        grants = listOf(
                            Grant("alice", setOf(Permission.VIEW)),
                            Grant("bob", setOf(Permission.UPLOAD)),
                        ),
                    ),
                ),
            ),
        )

        compose.onNode(
            hasTestTag(UiTags.ShareAccess) and hasText("alice", substring = true),
            useUnmergedTree = true,
        ).assertExists()
        compose.onNode(
            hasTestTag(UiTags.ShareAccess) and hasText("bob", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `switching a share off keeps the change in the store`() {
        val store = show(AppState(users = listOf(alice), shares = listOf(photos)))

        compose.onTag(UiTags.ShareEnabled).assertIsOn()
        compose.tap(UiTags.ShareEnabled)

        assertThat(store.state.shares.single().enabled).isFalse()
    }

    @Test
    fun `adding a share opens the folder picker`() {
        show(AppState(users = listOf(alice), shares = listOf(photos)))

        compose.tap(UiTags.AddShare)

        compose.onTag(UiTags.FolderPicker).assertIsDisplayed()
    }
}
