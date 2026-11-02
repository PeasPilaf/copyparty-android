package com.peaspilaf.copyparty

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.peaspilaf.copyparty.data.AndroidStorageAccess
import com.peaspilaf.copyparty.server.PythonBridge
import com.peaspilaf.copyparty.server.ServerService
import com.peaspilaf.copyparty.server.ServerState
import com.peaspilaf.copyparty.server.ServerStatus
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * J31 — the self-hosted build can serve a folder the user actually keeps files in.
 *
 * This is the P0 bug in its original form: `StorageAccess.allFilesAccessIntent()` and `browseRoots()`
 * had no call sites, so the picker could only walk inside the app's private folder and a self-hosted
 * owner had nothing worth sharing. The test grants All files access the way the system does, opens the
 * picker, walks into Internal storage -> Download, picks it, starts the server and proves the address
 * serves a file that is really there - not a copy in the app's folder.
 *
 * Runs against the self-hosted (`foss`) debug build, the only one allowed to ask for that access.
 */
@RunWith(AndroidJUnit4::class)
class FossStorageJourneyTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun storageAccess(): AndroidStorageAccess = AndroidStorageAccess(context)

    private val folderName = "Download"
    private val fileName = "foss-journey.txt"
    private val fileContents = "hello from a real folder\n"

    @Before
    fun grantAllFilesAccessAndSeedTheFolder() {
        stopTheServer()
        File(context.filesDir, "app-state.json").delete()

        // The grant the user makes in Settings -> Apps -> Special access -> All files access, done the
        // way the platform stores it so the app's own check sees it.
        shell("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        shell("mkdir -p /sdcard/$folderName")
        shell("echo hello from a real folder > /sdcard/$folderName/$fileName")

        recreateActivity()
    }

    @After
    fun stopEverything() {
        stopTheServer()
        shell("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE deny")
    }

    @Test
    fun `granting all files access lets the picker reach internal storage and serve a real folder`() {
        assertTrue(
            "the grant did not reach the app: hasAllFilesAccess() is still false",
            storageAccess().hasAllFilesAccess(),
        )

        compose.awaitTag(JourneyTags.FIRST_RUN_CHECKLIST)
        compose.tap(JourneyTags.NAV_SHARES)
        compose.tap(JourneyTags.ADD_SHARE)

        // Both roots are offered now; the phone root is the one that was missing.
        compose.awaitTag(JourneyTags.PICKER_ROOT_PHONE)
        compose.awaitTag(JourneyTags.PICKER_ROOT_APP)
        compose.tap(JourneyTags.PICKER_ROOT_PHONE)

        compose.tapTextWhenShown(folderName)
        compose.waitUntil(30_000) {
            compose.textOrNull(JourneyTags.PICKER_BREADCRUMB)?.contains(folderName) == true
        }

        compose.tap(JourneyTags.PICKER_SELECT)
        finishPickingIfTheEditorOpens()
        compose.awaitTag(JourneyTags.SHARE_ROW)

        compose.tap(JourneyTags.NAV_HOME)
        compose.tap(JourneyTags.HOME_START)
        compose.awaitTag(JourneyTags.HOME_ADDRESS, timeoutMs = 120_000)
        val address = addressIn(compose.textOf(JourneyTags.HOME_ADDRESS))

        compose.tap(JourneyTags.NAV_SHARES)
        val shareUrl = absoluteShareUrl(address, compose.textOf(JourneyTags.SHARE_URL)).trimEnd('/')

        awaitHttpContains(shareUrl, fileName)
        assertArrayEquals(
            "the served folder did not serve the file that is really in Download",
            fileContents.toByteArray(Charsets.UTF_8),
            awaitHttpBytes("$shareUrl/$fileName"),
        )

        // And it is really the external folder, not a copy inside the app's private storage.
        assertFalse(
            "the file ended up in the app's own folder, so the share is not the folder the user picked",
            File(AndroidStorageAccess(context).appShareRoot(), fileName).exists(),
        )
    }

    // -- Steps ---------------------------------------------------------------------------------

    private fun finishPickingIfTheEditorOpens() {
        compose.waitUntil(60_000) {
            nodeExists(JourneyTags.SHARE_ROW) || nodeExists(JourneyTags.SHARE_EDITOR_SAVE)
        }
        if (nodeExists(JourneyTags.SHARE_EDITOR_SAVE)) {
            compose.tap(JourneyTags.SHARE_EDITOR_SAVE)
        }
    }

    private fun nodeExists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun recreateActivity() {
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    /** Runs a command as the shell user, the way `adb shell` would, and waits for it to finish. */
    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }

    /** Leaves the device as it was: no foreground service, no copyparty process holding the port. */
    private fun stopTheServer() {
        runCatching {
            context.startService(
                Intent(context, ServerService::class.java).setAction(ServerService.ACTION_STOP),
            )
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (ServerState.status.value !is ServerStatus.Stopped && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
        }
        runCatching { context.stopService(Intent(context, ServerService::class.java)) }
        runCatching { PythonBridge.stop() }
        ServerState.set(ServerStatus.Stopped)
    }
}
