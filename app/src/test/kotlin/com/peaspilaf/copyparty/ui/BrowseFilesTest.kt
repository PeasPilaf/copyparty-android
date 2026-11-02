package com.peaspilaf.copyparty.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.browse.FakeBrowseFilePicker
import com.peaspilaf.copyparty.browse.FakeCopypartyApi
import com.peaspilaf.copyparty.browse.FakeDownloadSaver
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
 * The native file browser promise: opening a shared folder shows what is in it, folders are opened by
 * tapping them, the breadcrumb says where you are, and back is a ladder - one folder up at a time,
 * and only the last back leaves the screen.
 *
 * The share's permissions are the owner's permissions: this is the phone browsing its own server.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BrowseFilesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val api = FakeCopypartyApi()
    private val saver = FakeDownloadSaver()
    private val picker = FakeBrowseFilePicker()
    private var backs = 0

    private val share = Share(
        id = "s-public",
        name = "Public",
        urlPath = "public",
        localPath = "/storage/emulated/0/Public",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    private fun openShare() {
        // The server's order: a file first, the folder second. The screen has to put the folder on top.
        api.putFile("", "hello.txt", "hello\n")
        api.putFolder("", "sub")
        api.putFile("sub", "b.txt", "b\n")
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                BrowseScreen(
                    share = share,
                    baseUrl = "http://127.0.0.1:3923",
                    onBack = { backs += 1 },
                    api = api,
                    saver = saver,
                    picker = picker,
                )
            }
        }
        awaitText("hello.txt")
    }

    @Test
    fun `opening a share lists its files and folders`() {
        openShare()

        compose.onTag("browse-list").assertExists()
        compose.onAllTags("browse-row").assertCountEquals(2)
        // Folders come first: the fake server listed "hello.txt" before "sub", so the order proves it.
        val rows = rowTexts()
        assertThat(rows[0]).contains("sub")
        assertThat(rows[1]).contains("hello.txt")
        // The listing is aimed at this share's mount point, not at the server root.
        assertThat(api.baseUrls.first().trimEnd('/')).isEqualTo("http://127.0.0.1:3923/public")
    }

    @Test
    fun `tapping a folder moves into it and updates the breadcrumb`() {
        openShare()

        compose.onAllTags("browse-row")[0].performClick()

        awaitText("b.txt")
        compose.onAllTags("browse-row").assertCountEquals(1)
        compose
            .onNode(hasTestTag("browse-breadcrumb") and hasText("/sub", substring = true), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `back goes up one folder and only leaves at the root`() {
        openShare()
        compose.onAllTags("browse-row")[0].performClick()
        awaitText("b.txt")

        compose.tap("browse-back")

        awaitText("hello.txt")
        compose.onNode(hasTestTag("browse-breadcrumb") and hasText("/"), useUnmergedTree = true).assertExists()
        assertThat(backs).isEqualTo(0)

        compose.tap("browse-back")
        compose.waitUntil(5_000) { backs == 1 }
    }

    @Test
    fun `the up action returns to the parent folder`() {
        openShare()
        compose.onAllTags("browse-row")[0].performClick()
        awaitText("b.txt")
        compose.onTag("browse-up").assertExists()

        compose.tap("browse-up")

        awaitText("hello.txt")
        // At the root there is nowhere to go up to, so the action is gone instead of doing nothing.
        compose.onTag("browse-up").assertDoesNotExist()
    }

    @Test
    fun `the system back button goes up a folder before leaving the screen`() {
        openShare()
        compose.onAllTags("browse-row")[0].performClick()
        awaitText("b.txt")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitText("hello.txt")
        assertThat(backs).isEqualTo(0)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { backs == 1 }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The visible text of every row, in the order the screen shows them, for the folder-first promise. */
    private fun rowTexts(): List<String> =
        compose.onAllTags("browse-row").fetchSemanticsNodes().map { row ->
            row.children.flatMap { child ->
                child.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
            }.joinToString(" ")
        }
}
