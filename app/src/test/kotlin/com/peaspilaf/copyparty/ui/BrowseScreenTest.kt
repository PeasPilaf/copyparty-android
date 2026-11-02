package com.peaspilaf.copyparty.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.browse.CopypartyException
import com.peaspilaf.copyparty.browse.FakeBrowseFilePicker
import com.peaspilaf.copyparty.browse.FakeCopypartyApi
import com.peaspilaf.copyparty.browse.FakeDownloadSaver
import com.peaspilaf.copyparty.browse.tempUploadFile
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The native file screen's promises beyond navigation: a download tells you where it went, a failure
 * offers to try again, a folder that needs a password asks for it, a read-only folder offers no
 * upload, and choosing a file really uploads it into the folder you are looking at.
 *
 * All the network and storage work is faked at the seam the screen owns, so the test observes the
 * user's journey without touching a server or the phone's Downloads folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BrowseScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val api = FakeCopypartyApi()
    private val saver = FakeDownloadSaver()
    private val picker = FakeBrowseFilePicker()
    private var backs = 0

    private val writable = Share(
        id = "s-public",
        name = "Public",
        urlPath = "public",
        localPath = "/storage/emulated/0/Public",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW, Permission.UPLOAD))),
    )

    private val readOnly = writable.copy(
        id = "s-read-only",
        name = "Read only",
        urlPath = "read-only",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    private fun show(share: Share = writable, baseUrl: String = "http://127.0.0.1:3923") {
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                BrowseScreen(
                    share = share,
                    baseUrl = baseUrl,
                    onBack = { backs += 1 },
                    api = api,
                    saver = saver,
                    picker = picker,
                )
            }
        }
    }

    @Test
    fun `a download reports where the file was saved`() {
        api.putFile("", "report.pdf", "pdf-bytes")
        show()
        awaitText("report.pdf")

        compose.onAllTags("browse-download")[0].performClick()

        awaitText("Saved: Downloads/report.pdf")
        compose.onTag("browse-open").assertExists()
        compose.onTag("browse-share").assertExists()
        assertThat(saver.saved).containsExactly("report.pdf")
        assertThat(api.downloaded).containsExactly("report.pdf")
    }

    @Test
    fun `a download failure offers retry`() {
        api.putFile("", "report.pdf", "pdf-bytes")
        api.downloadFailure = CopypartyException.Unreachable("the connection dropped")
        show()
        awaitText("report.pdf")

        compose.onAllTags("browse-download")[0].performClick()

        awaitText("the connection dropped")
        compose.onTag("browse-retry").assertExists()

        api.downloadFailure = null
        compose.tap("browse-retry")

        awaitText("Saved: Downloads/report.pdf")
    }

    @Test
    fun `a read-only share hides the upload action`() {
        api.putFile("", "a.txt", "a")
        show(share = readOnly)
        awaitText("a.txt")

        // Offering upload on a folder with no upload grant would be a button that silently fails.
        compose.onTag("browse-upload").assertDoesNotExist()
    }

    @Test
    fun `a password protected share asks for the password and retries`() {
        api.requiredPassword = "hunter2"
        api.putFile("", "secret.txt", "top secret")
        show()
        compose.waitUntil(5_000) {
            compose.onAllTags("browse-auth-field").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onTag("browse-auth-submit").assertExists()

        compose.onTag("browse-auth-field").performTextInput("hunter2")
        compose.tap("browse-auth-submit")

        awaitText("secret.txt")
        assertThat(api.lastPassword).isEqualTo("hunter2")
    }

    @Test
    fun `an upload puts the chosen file into the folder it is showing`() {
        api.putFolder("", "sub")
        api.putFile("sub", "b.txt", "b\n")
        picker.chosen = tempUploadFile("notes.txt", "uploaded")
        show()
        compose.waitUntil(5_000) { compose.onAllTags("browse-row").fetchSemanticsNodes().isNotEmpty() }

        compose.onAllTags("browse-row")[0].performClick()
        awaitText("b.txt")
        compose.tap("browse-upload")

        awaitText("notes.txt")
        assertThat(picker.picks).isEqualTo(1)
        assertThat(api.uploads).containsExactly("sub/notes.txt" to "uploaded")
    }

    @Test
    fun `an unreachable server shows the error and a retry`() {
        api.listFailure = CopypartyException.Unreachable("the server did not answer")
        show()

        awaitText("the server did not answer")
        compose.onTag("browse-retry").assertExists()

        api.listFailure = null
        api.putFile("", "a.txt", "a")
        compose.tap("browse-retry")

        awaitText("a.txt")
    }

    @Test
    fun `an empty folder says it is empty`() {
        show()

        compose.waitUntil(5_000) {
            compose.onAllTags("browse-empty").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a downloaded file can be opened through the app file provider`() {
        // Downloads that land in the app's own folder are opened (and shared) through the app's
        // FileProvider; without the manifest entry and file_paths.xml this is a dead action.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val external = checkNotNull(context.getExternalFilesDir(null)) { "no external files folder in this test" }
        val file = File(external, "downloaded-report.pdf").apply { writeText("pdf") }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("${context.packageName}.files")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
