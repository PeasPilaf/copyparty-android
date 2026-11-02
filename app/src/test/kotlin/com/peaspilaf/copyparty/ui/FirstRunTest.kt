package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The first-run promise: a fresh install is told what to do in two steps, the Start button never
 * invites a tap that is guaranteed to fail, and the first step leads straight to the folder picker.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FirstRunTest {

    @get:Rule
    val compose = createComposeRule()

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    private fun show(state: AppState = AppState()) {
        val store = FakeConfigStore(state)
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store))
            }
        }
    }

    @Test
    fun `a fresh install shows a first-run checklist instead of a bare Start button`() {
        show()

        compose.onTag(UiTags.FirstRunChecklist).assertExists()
        compose.assertTextShown("Choose a folder")
        compose.assertTextShown("Start serving")
    }

    @Test
    fun `Start is disabled on an empty app and names the missing step`() {
        show()

        compose.onTag(UiTags.HomeStart).assertIsNotEnabled()
        compose.assertTextShown("Choose a folder")
    }

    @Test
    fun `the first-run action opens the folder picker`() {
        show()

        compose.tap(UiTags.FirstRunChooseFolder)

        compose.onTag(UiTags.FolderPicker).assertExists()
    }

    @Test
    fun `after the first share exists the checklist is gone and Start works`() {
        show(AppState(shares = listOf(photos)))

        compose.onTag(UiTags.FirstRunChecklist).assertDoesNotExist()
        compose.onTag(UiTags.HomeStart).assertIsEnabled()
    }
}
