package com.peaspilaf.copyparty.data

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.BuildConfig
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Which folders the app may offer for sharing without asking for any permission: a private folder
 * that always exists under the app's own storage, is always reachable from the folder picker, and
 * a picker that lists folders in a predictable order and hides Android's dot-folders.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StorageAccessTest {
    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var storage: AndroidStorageAccess

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        storage = AndroidStorageAccess(context)
    }

    @Test
    fun `the app share folder is created inside app private storage`() {
        val root = storage.appShareRoot()

        assertThat(root.isDirectory).isTrue()
        assertThat(root.canonicalPath.startsWith(context.filesDir.canonicalPath)).isTrue()
    }

    @Test
    fun `the app share folder comes back after it is deleted`() {
        val root = storage.appShareRoot()
        assertThat(root.deleteRecursively()).isTrue()

        val recreated = storage.appShareRoot()

        assertThat(recreated.isDirectory).isTrue()
        assertThat(recreated.canonicalPath).isEqualTo(root.canonicalPath)
    }

    @Test
    fun `the folder picker can always browse the app share folder`() {
        // TODO(server-runtime implementer): this needs AndroidStorageAccess.hasAllFilesAccess() to
        //  treat an unavailable platform answer as "no All files access". On a JVM test host
        //  Environment.isExternalStorageManager() cannot answer (the platform user environment is
        //  not initialised) and must not crash the folder picker - on a phone the app just falls
        //  back to its private share folder, which is exactly what this test pins down.
        val roots = storage.browseRoots().map { it.canonicalPath }

        assertThat(roots).contains(storage.appShareRoot().canonicalPath)
    }

    @Test
    fun `folders are listed alphabetically ignoring case and hidden ones are skipped`() {
        val dir = File(context.filesDir, "picker").apply { mkdirs() }
        for (name in listOf("beta", "Alpha", "gamma", "zulu", ".hidden")) {
            File(dir, name).mkdirs()
        }
        File(dir, "afile.txt").writeText("not a folder")
        File(dir, ".hiddenfile").writeText("not a folder either")

        val names = storage.listFolders(dir).map { it.name }

        assertThat(names).containsExactly("Alpha", "beta", "gamma", "zulu").inOrder()
    }

    @Test
    fun `the build says whether it can offer folders outside the app at all`() {
        // The Play build cannot (and must not ask for) broad storage access; the self-hosted build can.
        assertThat(storage.supportsExternalFolders).isEqualTo(BuildConfig.ALL_FILES_ACCESS)
    }

    @Test
    fun `the All files access intent opens this app's own settings page`() {
        val intent = storage.allFilesAccessIntent()

        assertThat(intent.action).isEqualTo(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        assertThat(intent.data).isEqualTo(Uri.parse("package:${context.packageName}"))
    }

    @Test
    fun `opening All files access prefers this app's own screen`() {
        registerSettingsScreen(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package")
        registerSettingsScreen(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        registerSettingsScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package")

        assertThat(storage.openAllFilesAccess()).isTrue()

        val opened = shadowOf(application).nextStartedActivity
        assertThat(opened.action).isEqualTo(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        assertThat(opened.data).isEqualTo(Uri.parse("package:${context.packageName}"))
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun `opening All files access falls back to the global list when the app screen is missing`() {
        // Some OEM Settings builds never register the per-app action, but do have the full list.
        registerSettingsScreen(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

        assertThat(storage.openAllFilesAccess()).isTrue()

        val opened = shadowOf(application).nextStartedActivity
        assertThat(opened.action).isEqualTo(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        assertThat(opened.data).isNull()
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun `opening All files access falls back to this app's details page as the last route`() {
        registerSettingsScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package")

        assertThat(storage.openAllFilesAccess()).isTrue()

        val opened = shadowOf(application).nextStartedActivity
        assertThat(opened.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(opened.data).isEqualTo(Uri.parse("package:${context.packageName}"))
    }

    @Test
    fun `opening All files access reports failure and starts nothing when no screen exists`() {
        assertThat(storage.openAllFilesAccess()).isFalse()

        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun `a Settings screen that throws is skipped instead of ending the attempt`() {
        // The screen is advertised, but this OEM build rejects it: that must be a fallback, not a
        // dead end. The wrapper throws only for the app-specific action.
        val throwingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                if (intent.action == Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) {
                    throw ActivityNotFoundException("OEM Settings has no per-app page")
                }
                super.startActivity(intent)
            }
        }
        registerSettingsScreen(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package")
        registerSettingsScreen(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

        assertThat(AndroidStorageAccess(throwingContext).openAllFilesAccess()).isTrue()

        val opened = shadowOf(application).nextStartedActivity
        assertThat(opened.action).isEqualTo(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    /** Registers a Settings activity that handles [action], the way a device's Settings app does. */
    private fun registerSettingsScreen(action: String, dataScheme: String? = null) {
        val shadowPackageManager = shadowOf(context.packageManager)
        val component = ComponentName("com.android.settings", "com.android.settings.CopypartyTestSettings")
        shadowPackageManager.addActivityIfNotPresent(component)
        val filter = IntentFilter(action)
        if (dataScheme != null) filter.addDataScheme(dataScheme)
        shadowPackageManager.addIntentFilterForActivity(component, filter)
    }
}
