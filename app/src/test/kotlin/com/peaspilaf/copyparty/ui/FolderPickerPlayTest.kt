package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The store build's honesty promise: it cannot serve folders outside the app, so it says exactly
 * that and never offers a permission button that the Play policy would refuse anyway. The useful
 * workaround - copying files into the app's folder - is offered instead.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FolderPickerPlayTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(storage: FakeStorageAccess) {
        val store = FakeConfigStore(AppState())
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store, storage = storage))
            }
        }
        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.AddShare)
        compose.onTag(UiTags.FolderPicker).assertExists()
    }

    @Test
    fun `the store build explains that it can share only the app folder`() {
        show(FakeStorageAccess(supportsExternalFolders = false))

        compose.assertTextShown("This build can share only the app")
        compose.assertTextShown("any folder")
        compose.onTag(UiTags.PickerRootApp).assertExists()
    }

    @Test
    fun `the store build never offers an All files access action`() {
        show(FakeStorageAccess(supportsExternalFolders = false, allFilesAccess = false))

        compose.onTag(UiTags.StorageGrant).assertDoesNotExist()
        compose.onTag(UiTags.PickerRootPhone).assertDoesNotExist()
    }

    @Test
    fun `the store build offers Add files so the app folder can hold something`() {
        show(FakeStorageAccess(supportsExternalFolders = false))

        compose.tap(UiTags.NavHome)
        compose.onTag(UiTags.ImportFiles).assertExists()
    }
}
