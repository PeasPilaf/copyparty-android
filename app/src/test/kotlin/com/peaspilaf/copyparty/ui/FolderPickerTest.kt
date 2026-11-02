package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The folder picker promise: both places a folder can live are one tap from the start, the
 * breadcrumb says where you are, Use this folder means "share this one", and a folder the app is
 * not allowed to read says so instead of pretending to be empty.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FolderPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private fun storage(): FakeStorageAccess {
        val storage = FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = true)
        File(storage.appRoot, "Photos").mkdirs()
        File(storage.appRoot, "Music").mkdirs()
        File(storage.phoneRoot, "Downloads").mkdirs()
        return storage
    }

    private fun show(storage: FakeStorageAccess): FakeConfigStore {
        val store = FakeConfigStore(AppState())
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store, storage = storage))
            }
        }
        compose.tap(UiTags.NavShares)
        compose.tap(UiTags.AddShare)
        compose.onTag(UiTags.FolderPicker).assertExists()
        return store
    }

    private fun breadcrumb(text: String) = compose.onNode(
        hasTestTag(UiTags.PickerBreadcrumb) and hasText(text, substring = true),
        useUnmergedTree = true,
    )

    @Test
    fun `the picker offers both the app folder and internal storage as roots`() {
        show(storage())

        compose.onTag(UiTags.PickerRootApp).assertExists()
        compose.onTag(UiTags.PickerRootPhone).assertExists()
        compose.assertTextShown("App folder")
        compose.assertTextShown("This phone")
    }

    @Test
    fun `walking into folders updates the breadcrumb and the selected path`() {
        val storage = storage()
        show(storage)

        compose.tap(UiTags.PickerRootApp)
        breadcrumb("App folder").assertExists()

        compose.tap(UiTags.pickerFolder("Photos"))
        breadcrumb("Photos").assertExists()

        compose.tap(UiTags.PickerUp)
        breadcrumb("Photos").assertDoesNotExist()

        compose.tap(UiTags.pickerFolder("Photos"))
        compose.tap(UiTags.PickerSelect)

        compose.onNode(
            hasTestTag(UiTags.EditorFolder) and
                hasText(File(storage.appRoot, "Photos").absolutePath, substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `Use this folder creates a share rooted at the shown folder`() {
        val storage = storage()
        val store = show(storage)

        compose.tap(UiTags.PickerRootApp)
        compose.tap(UiTags.pickerFolder("Photos"))
        compose.tap(UiTags.PickerSelect)
        compose.onTag(UiTags.ShareEditor).assertExists()

        compose.tap(UiTags.PresetAnyoneView)
        compose.tap(UiTags.ShareEditorSave)

        assertThat(store.state.shares.single().localPath)
            .isEqualTo(File(storage.appRoot, "Photos").absolutePath)
    }

    @Test
    fun `an unreadable folder is reported as unreadable instead of empty`() {
        val storage = FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = false)
        val locked = File(storage.appRoot, "Locked").apply { mkdirs() }
        storage.makeUnreadable(locked)
        show(storage)

        compose.tap(UiTags.PickerRootApp)
        compose.tap(UiTags.pickerFolder("Locked"))

        compose.onTag(UiTags.PickerUnreadable).assertExists()
        compose.onTag(UiTags.PickerEmpty).assertDoesNotExist()
        // The way out is the same grant action the picker offers at the roots.
        compose.onTag(UiTags.StorageGrant).assertExists()
    }
}
