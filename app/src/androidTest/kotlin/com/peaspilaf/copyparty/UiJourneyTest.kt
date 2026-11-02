package com.peaspilaf.copyparty

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.data.AndroidStorageAccess
import com.peaspilaf.copyparty.server.PythonBridge
import com.peaspilaf.copyparty.server.ServerService
import com.peaspilaf.copyparty.server.ServerState
import com.peaspilaf.copyparty.server.ServerStatus
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * J17/J30 — the novice journey, on a real phone: the app folder, a real file, the real embedded
 * copyparty, the real screens.
 *
 * This is the gate for the UX bugs that shipped: a share could not be created from a folder the user
 * controls, the served folder could never contain a user file, and a share could never be deleted -
 * and every unit test still passed. So these tests create a share from Home's first-run state, start
 * the server, prove that the address Home shows really serves the file, browse and download it in the
 * app, upload through a real client, and delete the share again.
 *
 * Nothing here is faked: `MainActivity`, the foreground service and the embedded interpreter are the
 * shipped ones. Only two things are arranged instead of clicked: the notification-permission answer
 * (a system dialog) and, in one test, a share that allows uploads - the in-app upload has to go
 * through the system file picker, which an instrumented test cannot drive.
 */
@RunWith(AndroidJUnit4::class)
class UiJourneyTest {

    // A real device shows the notification-permission dialog on the first start; the answer is given
    // up front so the journey is about the server, not about a system dialog.
    @get:Rule(order = 0)
    val notifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val shareRoot: File get() = AndroidStorageAccess(context).appShareRoot()

    private val fileName = "journey-file.txt"
    private val fileContents = "hello from the novice journey\n"

    @Before
    fun freshInstall() {
        stopTheServer()
        File(context.filesDir, "app-state.json").delete()
        shareRoot.mkdirs()
        File(shareRoot, fileName).writeText(fileContents)
        recreateActivity()
    }

    @After
    fun stopEverything() {
        stopTheServer()
    }

    @Test
    fun `a novice can create a share, start, browse, download and delete it`() {
        createShareFromTheAppFolder()
        startTheServer()
        val address = homeAddress()
        val shareUrl = shareUrlFromTheList(address)

        // The address Home offered must really serve the file the phone holds.
        assertArrayEquals(
            "the address on Home did not serve the shared file",
            fileContents.toByteArray(Charsets.UTF_8),
            awaitHttpBytes("$shareUrl/$fileName"),
        )

        // Browse it in the app: the listing shows the file and its download control does something.
        compose.tap(JourneyTags.HOME_BROWSE)
        compose.awaitTag(JourneyTags.BROWSE_LIST)
        awaitTextInUnmergedTree(fileName)
        // A file left behind by an earlier run must not count as proof that this tap did anything.
        val downloadStartedAt = System.currentTimeMillis()
        compose.firstTag(JourneyTags.BROWSE_DOWNLOAD).performClick()
        awaitDownloaded(
            fileName,
            fileContents.toByteArray(Charsets.UTF_8),
            since = downloadStartedAt,
        )
        compose.tap(JourneyTags.BROWSE_BACK)

        // And the share can be removed again, which is what the review found impossible.
        compose.tap(JourneyTags.NAV_SHARES)
        compose.tap(JourneyTags.SHARE_DELETE)
        compose.tap(JourneyTags.SHARE_DELETE_CONFIRM)
        compose.awaitNoTag(JourneyTags.SHARE_ROW)
    }

    @Test
    fun `the address shown on Home really serves the shared folder`() {
        createShareFromTheAppFolder()
        startTheServer()

        val address = homeAddress()
        val root = String(awaitHttpBytes(address), Charsets.UTF_8).lowercase()
        assertTrue(
            "the address Home shows does not serve copyparty's own page: $address",
            root.contains("copyparty"),
        )

        val shareUrl = shareUrlFromTheList(address)
        awaitHttpContains(shareUrl, fileName)
        assertArrayEquals(
            "the shared folder did not serve the file byte for byte",
            fileContents.toByteArray(Charsets.UTF_8),
            awaitHttpBytes("$shareUrl/$fileName"),
        )
    }

    @Test
    fun `deleting the last share returns the app to the first-run state`() {
        createShareFromTheAppFolder()
        compose.tap(JourneyTags.NAV_SHARES)

        compose.tap(JourneyTags.SHARE_EDIT)
        compose.tap(JourneyTags.EDITOR_DELETE)
        compose.tap(JourneyTags.EDITOR_DELETE_CONFIRM)

        compose.awaitNoTag(JourneyTags.SHARE_ROW)
        compose.tap(JourneyTags.NAV_HOME)
        compose.awaitTag(JourneyTags.FIRST_RUN_CHECKLIST)

        // A deletion that only lived in one screen's memory would come back on rotation.
        recreateActivity()
        compose.awaitTag(JourneyTags.FIRST_RUN_CHECKLIST)
        compose.tap(JourneyTags.NAV_SHARES)
        compose.awaitNoTag(JourneyTags.SHARE_ROW)
        assertTrue(
            "the deleted share came back after the activity was recreated",
            compose.onAllNodesWithTag(JourneyTags.SHARE_ROW, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `a file a client uploads shows up in the app's listing`() {
        // The old bug: the folder the app served could never contain a user file. Uploading needs a
        // grant, and an in-app upload goes through the system picker, so the grant is arranged here
        // and the upload is done by a real HTTP client - the same thing the phone's browser does.
        seedShareThatAllowsUploads()
        startTheServer()
        val address = homeAddress()
        val shareUrl = shareUrlFromTheList(address)

        compose.tap(JourneyTags.HOME_BROWSE)
        compose.awaitTag(JourneyTags.BROWSE_LIST)
        awaitTextInUnmergedTree(fileName)
        compose.tap(JourneyTags.BROWSE_BACK)

        val uploaded = "put-here-by-a-client.txt"
        val body = "a client put this here\n"
        httpPut("$shareUrl/$uploaded", body.toByteArray(Charsets.UTF_8))

        compose.tap(JourneyTags.HOME_BROWSE)
        compose.awaitTag(JourneyTags.BROWSE_LIST)
        awaitTextInUnmergedTree(uploaded)
        assertArrayEquals(
            "the uploaded file did not read back",
            body.toByteArray(Charsets.UTF_8),
            awaitHttpBytes("$shareUrl/$uploaded"),
        )
        compose.tap(JourneyTags.BROWSE_BACK)
    }

    // -- The journey, step by step -------------------------------------------------------------

    private fun createShareFromTheAppFolder() {
        // A fresh install explains the first step instead of showing an empty screen.
        compose.awaitTag(JourneyTags.FIRST_RUN_CHECKLIST)
        compose.tap(JourneyTags.NAV_SHARES)
        compose.tap(JourneyTags.ADD_SHARE)
        compose.awaitTag(JourneyTags.PICKER_ROOT_APP)
        compose.tap(JourneyTags.PICKER_ROOT_APP)
        compose.tap(JourneyTags.PICKER_SELECT)
        finishPickingIfTheEditorOpens()
        compose.awaitTag(JourneyTags.SHARE_ROW)
    }

    /**
     * Picking a folder may open the share editor. The editor's Save is the way out; the journey does
     * not care whether the pick itself or an explicit Save creates the share, only that it exists.
     */
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

    private fun seedShareThatAllowsUploads() {
        val store = (context.applicationContext as CopypartyApp).container.configStore
        store.save(
            AppState(
                shares = listOf(
                    Share(
                        id = "journey",
                        name = "Journey",
                        urlPath = "journey",
                        localPath = shareRoot.absolutePath,
                        grants = listOf(
                            Grant(Grant.EVERYONE, setOf(Permission.VIEW, Permission.UPLOAD)),
                        ),
                    ),
                ),
            ),
        )
        recreateActivity()
    }

    private fun startTheServer() {
        compose.tap(JourneyTags.NAV_HOME)
        compose.tap(JourneyTags.HOME_START)
        // The address only appears once copyparty itself answered on the port.
        compose.awaitTag(JourneyTags.HOME_ADDRESS, timeoutMs = 120_000)
    }

    private fun homeAddress(): String = addressIn(compose.textOf(JourneyTags.HOME_ADDRESS))

    private fun shareUrlFromTheList(address: String): String {
        compose.tap(JourneyTags.NAV_SHARES)
        val shareText = compose.textOf(JourneyTags.SHARE_URL)
        // Back to Home, where the browser, the QR code and the address live.
        compose.tap(JourneyTags.NAV_HOME)
        return absoluteShareUrl(address, shareText).trimEnd('/')
    }

    private fun awaitTextInUnmergedTree(text: String, timeoutMs: Long = 60_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun recreateActivity() {
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private fun httpPut(url: String, body: ByteArray) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 20_000
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) throw AssertionError("PUT $url returned $status")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A download is only real if something happened. The in-app download may write into a Downloads
     * folder, hand the file to Android's DownloadManager, or save it through the app's own saver and
     * say so on screen; all three are accepted, because the promise is "tapping download produces the
     * file", not "it uses one particular API". When the file is found its bytes are checked too, so a
     * download that saved the wrong thing fails as well - and only a file written after [since] counts,
     * so a leftover from an earlier run cannot make a dead click look successful.
     */
    private fun awaitDownloaded(
        name: String,
        expected: ByteArray,
        since: Long,
        timeoutMs: Long = 120_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            // Two seconds of slack: the filesystem timestamp is taken after the click, never before it.
            val saved = downloadFolders()
                .map { File(it, name) }
                .firstOrNull { it.isFile && it.length() > 0 && it.lastModified() >= since - 2_000 }
            if (saved != null) {
                assertArrayEquals(
                    "the downloaded file does not match what the server served",
                    expected,
                    saved.readBytes(),
                )
                return
            }
            if (downloadManagerKnows(name) || savedConfirmationShown(name)) return
            Thread.sleep(500)
        }
        throw AssertionError(
            "tapping download produced nothing fresh: no '$name' written to ${downloadFolders()} " +
                "after the tap, no DownloadManager request and no saved confirmation on screen",
        )
    }

    private fun savedConfirmationShown(name: String): Boolean =
        compose.onAllNodes(
            hasText("Saved:", substring = true) and hasText(name, substring = true),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    @Suppress("DEPRECATION")
    private fun downloadFolders(): List<File> = listOfNotNull(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    )

    private fun downloadManagerKnows(name: String): Boolean {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return false
        val query = DownloadManager.Query()
        manager.query(query)?.use { cursor ->
            val title = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val uri = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            while (cursor.moveToNext()) {
                val known = listOfNotNull(
                    if (title >= 0) cursor.getString(title) else null,
                    if (uri >= 0) cursor.getString(uri) else null,
                )
                if (known.any { it.contains(name) }) return true
            }
        }
        return false
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
