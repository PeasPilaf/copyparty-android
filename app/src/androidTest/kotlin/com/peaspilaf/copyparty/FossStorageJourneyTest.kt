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

        // Diagnostics: what does the app itself see in that folder, and can it write there?
        // (If shell seeding is invisible to the app but an app write works, that difference is the
        // bug; if neither works, the appops grant is not effective in this process.)
        val realFolder = File("/storage/emulated/0/$folderName")
        val realFile = File(realFolder, fileName)
        val appWrite = runCatching {
            realFolder.mkdirs()
            realFile.writeText(fileContents)
            realFile.isFile
        }.isSuccess
        mark(
            context,
            "seed: appWrite=" + appWrite +
                " fileExists=" + realFile.isFile +
                " canRead=" + realFile.canRead() +
                " folderExists=" + realFolder.isDirectory +
                " appList=" + (realFolder.listFiles()?.take(8)?.joinToString { it.name } ?: "null") +
                " shellList=" + shell("ls -la /sdcard/$folderName 2>&1 | head -8").replace("\n", " | "),
        )

        recreateActivity()
    }

    @After
    fun stopEverything() {
        mark(context, "teardown: dumping diagnostics")
        dumpDiagnostics(context)
        mark(context, "teardown: stopping server")
        stopTheServer()
        mark(context, "teardown: done")
        shell("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE deny")
    }

    @Test
    fun grantingAllFilesAccessLetsThePickerReachInternalStorageAndServeARealFolder() {
        assertTrue(
            "the grant did not reach the app: hasAllFilesAccess() is still false",
            storageAccess().hasAllFilesAccess(),
        )

        mark(context, "granted: hasAllFilesAccess=" + storageAccess().hasAllFilesAccess())
        compose.awaitTag(JourneyTags.FIRST_RUN_CHECKLIST)
        mark(context, "first run checklist shown")
        compose.tap(JourneyTags.NAV_SHARES)
        compose.tap(JourneyTags.ADD_SHARE)

        // Both roots are offered now; the phone root is the one that was missing.
        compose.awaitTag(JourneyTags.PICKER_ROOT_PHONE)
        mark(context, "picker open with both roots")
        compose.awaitTag(JourneyTags.PICKER_ROOT_APP)
        compose.tap(JourneyTags.PICKER_ROOT_PHONE)

        compose.tapTextWhenShown(folderName)
        compose.waitUntil(30_000) {
            compose.textOrNull(JourneyTags.PICKER_BREADCRUMB)?.contains(folderName) == true
        }
        mark(context, "folder opened: " + (compose.textOrNull(JourneyTags.PICKER_BREADCRUMB) ?: "?"))

        compose.tap(JourneyTags.PICKER_SELECT)
        finishPickingIfTheEditorOpens()
        compose.awaitTag(JourneyTags.SHARE_ROW)
        mark(context, "share saved")

        compose.tap(JourneyTags.NAV_HOME)
        compose.tap(JourneyTags.HOME_START)
        mark(context, "start tapped")
        compose.awaitTag(JourneyTags.HOME_ADDRESS, timeoutMs = 120_000)
        val address = addressIn(compose.textOf(JourneyTags.HOME_ADDRESS))
        mark(context, "address shown: " + address + " status=" + ServerState.status.value)

        compose.tap(JourneyTags.NAV_SHARES)
        val shareText = compose.textOf(JourneyTags.SHARE_URL)
        assertTrue("the share link must be absolute, was '$shareText'", shareText.startsWith("http://"))
        assertTrue("the share link must not be loopback, was '$shareText'", !shareText.contains("127.0.0.1"))
        // Fetch over loopback: the emulator's own LAN interface is not a reliable route to itself.
        val shareUrl = localShareUrl(address, shareText)

        mark(context, "share link read: " + shareText + " -> local " + shareUrl)

        // Make the next failure explain itself instead of waiting two minutes for a silent timeout:
        // prove the saved share points at the folder the test seeded, and that the app can read it.
        val stateJson = File(context.filesDir, "app-state.json").readText()
        val localPath = runCatching {
            org.json.JSONObject(stateJson).getJSONArray("shares").getJSONObject(0).getString("localPath")
        }.getOrElse { throw AssertionError("no saved share; app-state.json was: " + stateJson) }
        val servedFile = File(localPath, fileName)
        val folder = servedFile.parentFile
        val diagnostics =
            "share=" + localPath +
                " fileExists=" + servedFile.isFile +
                " fileCanRead=" + servedFile.canRead() +
                " folderExists=" + (folder?.isDirectory) +
                " folderCanRead=" + (folder?.canRead()) +
                " folderList=" + (folder?.listFiles()?.take(10)?.joinToString { it.name } ?: "null") +
                " shellList=" + shell("ls -la " + localPath + " 2>&1 | head -10").replace("\n", " | ")
        mark(context, "serving check: " + diagnostics)
        assertTrue(
            "the saved share is rooted at '" + localPath + "' but '" + fileName + "' is not there; " + diagnostics,
            servedFile.isFile,
        )
        val listing = runCatching { String(httpGet(shareUrl.trimEnd('/') + "/"), Charsets.UTF_8) }
            .getOrElse {
                throw AssertionError(
                    "GET " + shareUrl + "/ failed; share=" + localPath +
                        " fileExists=" + servedFile.isFile + " status=" + ServerState.status.value,
                    it,
                )
            }
        assertTrue(
            "GET " + shareUrl + "/ did not list " + fileName + "; share=" + localPath +
                "; body=" + listing.take(1500),
            listing.contains(fileName),
        )

        assertArrayEquals(
            "the served folder did not serve the file that is really in Download",
            fileContents.toByteArray(Charsets.UTF_8),
            awaitHttpBytes(shareUrl.trimEnd('/') + "/" + fileName),
        )

        // And it is really the external folder, not a copy inside the app's private storage.
        assertFalse(
            "the file ended up in the app's own folder, so the share is not the folder the user picked",
            File(AndroidStorageAccess(context).appShareRoot(), fileName).exists(),
        )
        mark(context, "all assertions passed")

        // Stop through the UI, the way a person does, so teardown has nothing left to force.
        compose.tap(JourneyTags.NAV_HOME)
        compose.tap(JourneyTags.HOME_STOP)
        compose.awaitTag(JourneyTags.HOME_START)
        mark(context, "server stopped from the UI")
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
