package com.peaspilaf.copyparty.ui

import android.content.ComponentName
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.data.AndroidStorageAccess
import com.peaspilaf.copyparty.data.StorageAccess
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
 *
 * The Settings screen itself is not part of this app, so the tests decide which Settings activities
 * exist in the shadow package manager. That is how the OEM case - an app-specific screen that is
 * simply not there - is reproduced without a device, and it is why the app has ordered fallbacks
 * instead of one bare startActivity.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FolderPickerFossTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(storage: StorageAccess) {
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

    /**
     * Real storage access, but forced to claim external folders are supported. The test BuildConfig
     * stub has `ALL_FILES_ACCESS = false` (the Play default), so the production property would hide
     * the very button these tests are about.
     */
    private fun realStorage(): StorageAccess {
        val delegate = AndroidStorageAccess(compose.activity)
        return object : StorageAccess by delegate {
            override val supportsExternalFolders: Boolean get() = true
        }
    }

    /** Adds a Settings activity that handles [action], as an OEM build may or may not do. */
    private fun registerSettingsScreen(action: String, dataScheme: String? = null) {
        val shadowPackageManager = shadowOf(compose.activity.packageManager)
        val component = ComponentName("com.android.settings", "com.android.settings.CopypartyTestSettings")
        shadowPackageManager.addActivityIfNotPresent(component)
        val filter = IntentFilter(action)
        if (dataScheme != null) filter.addDataScheme(dataScheme)
        shadowPackageManager.addIntentFilterForActivity(component, filter)
    }

    @Test
    fun `the self-hosted build offers an All files access action while the permission is missing`() {
        show(FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = false))

        compose.onTag(UiTags.StorageGrant).assertExists()
        compose.onTag(UiTags.PickerRootPhone).assertDoesNotExist()
    }

    @Test
    fun `tapping the grant action asks storage to open the system screen`() {
        val storage = FakeStorageAccess(supportsExternalFolders = true, allFilesAccess = false)
        show(storage)

        compose.tap(UiTags.StorageGrant)

        assertThat(storage.openCalls).isEqualTo(1)
        assertThat(storage.lastOpenIntent?.action).isEqualTo(FakeStorageAccess.GRANT_ACTION)
    }

    @Test
    fun `tapping the grant action starts this app's own All files access screen`() {
        registerSettingsScreen(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package")
        show(realStorage())

        compose.tap(UiTags.StorageGrant)

        val opened = shadowOf(compose.activity).nextStartedActivity
        assertThat(opened).isNotNull()
        assertThat(opened.action).isEqualTo(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        assertThat(opened.data).isEqualTo(Uri.parse("package:${compose.activity.packageName}"))
        compose.onTag(StorageGrantHelp).assertDoesNotExist()
    }

    @Test
    fun `when the app page is missing the global All files access list is started`() {
        registerSettingsScreen(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        show(realStorage())

        compose.tap(UiTags.StorageGrant)

        val opened = shadowOf(compose.activity).nextStartedActivity
        assertThat(opened).isNotNull()
        assertThat(opened.action).isEqualTo(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        compose.onTag(StorageGrantHelp).assertDoesNotExist()
    }

    @Test
    fun `when no Settings screen exists the picker shows the manual steps instead of doing nothing`() {
        // Nothing registered: the OEM has neither the app-specific nor the global screen, and the
        // app details route is absent too. The tap must still produce a visible, dismissable answer.
        show(realStorage())

        compose.tap(UiTags.StorageGrant)

        compose.onTag(StorageGrantHelp).assertExists()
        compose.assertTextShown(
            "Settings -> Apps -> Special app access -> All files access -> Copyparty -> Allow",
        )
        assertThat(shadowOf(compose.activity).nextStartedActivity).isNull()

        compose.onTag(StorageGrantHelpDismiss).performClick()
        compose.onTag(StorageGrantHelp).assertDoesNotExist()
    }

    @Test
    fun `the Play build fake never shows the grant action`() {
        show(FakeStorageAccess(supportsExternalFolders = false, allFilesAccess = false))

        compose.onTag(UiTags.StorageGrant).assertDoesNotExist()
        compose.onTag(UiTags.PickerRootPhone).assertDoesNotExist()
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
