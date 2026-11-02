package com.peaspilaf.copyparty.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The system-back promise: leaving a screen with the back gesture goes back one step inside the
 * app - it never finishes the activity and drops the user out of the app they were using.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BackNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val url = "http://192.168.1.20:3923"

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    private fun show() {
        val viewModel = testViewModel(
            store = FakeConfigStore(AppState(shares = listOf(photos))),
            server = FakeServerRuntime(ServerStatus.Running(listOf(url), 3923)),
        )
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(viewModel)
            }
        }
    }

    private fun systemBack() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    @Test
    fun `back from the editor returns to the list`() {
        show()
        compose.tap(UiTags.NavShares)
        compose.tapInRow(UiTags.ShareRow, "Photos", UiTags.ShareEdit)
        compose.onTag(UiTags.ShareEditor).assertExists()

        systemBack()

        compose.onTag(UiTags.ShareEditor).assertDoesNotExist()
        compose.onTag(UiTags.ScreenShares).assertExists()
        compose.onTagInRow(UiTags.ShareRow, "Photos", UiTags.ShareEdit).assertExists()
    }

    @Test
    fun `back from the picker returns to the list`() {
        show()
        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.AddShare)
        compose.onTag(UiTags.FolderPicker).assertExists()

        systemBack()

        compose.onTag(UiTags.FolderPicker).assertDoesNotExist()
        compose.onTag(UiTags.ScreenShares).assertExists()
    }

    @Test
    fun `back from the full-screen routes returns to Home`() {
        show()

        compose.tap(UiTags.HomeQr)
        compose.onTag(UiTags.ScreenQr).assertExists()
        systemBack()
        compose.onTag(UiTags.ScreenQr).assertDoesNotExist()
        compose.onTag(UiTags.ScreenHome).assertExists()

        compose.tap(UiTags.HomeBrowse)
        // Owned by the native-expert region: the back control on BrowseScreen.
        compose.onTag(BROWSE_BACK).assertExists()
        systemBack()
        compose.onTag(BROWSE_BACK).assertDoesNotExist()
        compose.onTag(UiTags.ScreenHome).assertExists()
    }

    private companion object {
        /** The testTag the native-expert region puts on BrowseScreen's back control. */
        const val BROWSE_BACK = "browse-back"
    }
}
