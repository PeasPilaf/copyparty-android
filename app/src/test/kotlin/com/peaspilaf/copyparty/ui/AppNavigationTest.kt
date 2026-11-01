package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * The navigation promise: the bottom bar is the map of the app, and the share editor is one step
 * deep, so backing out of it puts you back on the list you came from.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AppNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        val store = FakeConfigStore(
            AppState(
                users = listOf(User("alice", "hunter2")),
                shares = listOf(
                    Share(
                        id = "s1",
                        name = "Photos",
                        urlPath = "photos",
                        localPath = "/storage/photos",
                        grants = listOf(Grant("alice", setOf(Permission.VIEW))),
                    ),
                ),
            ),
        )
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store))
            }
        }
    }

    @Test
    fun `the bottom bar reaches home shares people and settings`() {
        show()

        compose.onTag(UiTags.ScreenHome).assertIsDisplayed()

        compose.tap(UiTags.NavShares)
        compose.onTag(UiTags.ScreenShares).assertIsDisplayed()

        compose.tap(UiTags.NavPeople)
        compose.onTag(UiTags.ScreenPeople).assertIsDisplayed()

        compose.tap(UiTags.NavSettings)
        compose.onTag(UiTags.ScreenSettings).assertIsDisplayed()

        compose.tap(UiTags.NavHome)
        compose.onTag(UiTags.ScreenHome).assertIsDisplayed()
    }

    @Test
    fun `the back arrow in the share editor returns to the shares list`() {
        show()
        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.ShareEdit)
        compose.onTag(UiTags.ShareEditor).assertIsDisplayed()

        compose.tap(UiTags.ShareEditorBack)

        compose.onTag(UiTags.ShareEditor).assertDoesNotExist()
        compose.onTag(UiTags.ShareRow).assertIsDisplayed()
    }
}
