package com.peaspilaf.copyparty.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The self-hosted build's storage promise: while All files access is missing the picker says so and
 * offers the system action that fixes it; once the user grants it in system settings and comes back,
 * internal storage is a picker root without restarting the app.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FolderPickerFossTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
    fun `the self-hosted build offers an All files access action while the permission is missing`() {
        show(FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = false))

        compose.onTag(UiTags.StorageGrant).assertExists()
        compose.onTag(UiTags.PickerRootPhone).assertDoesNotExist()
    }

    @Test
    fun `tapping the grant action opens the system All files access screen`() {
        val storage = FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = false)
        show(storage)

        compose.tap(UiTags.StorageGrant)

        val opened = shadowOf(compose.activity).nextStartedActivity
        assertThat(opened).isNotNull()
        assertThat(opened.action).isEqualTo(FakeStorageAccess.GRANT_ACTION)
    }

    @Test
    fun `granting access makes internal storage a picker root`() {
        val storage = FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = false)
        show(storage)
        compose.onTag(UiTags.PickerRootPhone).assertDoesNotExist()

        // The user left the app for the system settings screen, granted access, and came back.
        storage.allFilesAccess = true
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onTag(UiTags.PickerRootPhone).assertExists()
    }

    @Test
    fun `the grant action disappears once access exists`() {
        show(FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = true))

        compose.onTag(UiTags.StorageGrant).assertDoesNotExist()
        compose.onTag(UiTags.PickerRootPhone).assertExists()
    }
}
