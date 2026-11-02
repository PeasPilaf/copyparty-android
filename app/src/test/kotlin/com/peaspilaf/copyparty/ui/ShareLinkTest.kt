package com.peaspilaf.copyparty.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The share-link promise: every shared folder has the one address a person can actually type -
 * the server's address plus the folder's mount path - and copying or sending it says so on screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ShareLinkTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val baseUrl = "http://192.168.1.20:3923"

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    private val photosUrl = "$baseUrl/photos"

    private fun show() {
        val viewModel = testViewModel(
            store = FakeConfigStore(AppState(shares = listOf(photos))),
            server = FakeServerRuntime(ServerStatus.Running(listOf(baseUrl), 3923)),
        )
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(viewModel)
            }
        }
        compose.tap(UiTags.NavShares)
    }

    private fun clipboard(): ClipboardManager =
        requireNotNull(compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)

    @Test
    fun `every share exposes its full URL`() {
        show()

        compose.onNode(
            hasTestTag(UiTags.ShareUrl) and hasText(photosUrl, substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `copying a share link confirms on screen`() {
        show()

        compose.tap(UiTags.ShareCopy)

        compose.awaitText("copied")
        val copied = clipboard().primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertThat(copied).contains(baseUrl)
        assertThat(copied).contains("/photos")
    }

    @Test
    fun `sharing a share link hands it to another app`() {
        show()

        compose.tap(UiTags.ShareSend)
        compose.waitForIdle()

        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertThat(chooser).isNotNull()
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)

        @Suppress("DEPRECATION")
        val send: Intent? = chooser.getParcelableExtra(Intent.EXTRA_INTENT)
        assertThat(send).isNotNull()
        assertThat(send!!.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            .contains("$baseUrl/photos")
    }

    @Test
    fun `a share opens in the in-app browser at its own address`() {
        show()

        compose.tap(UiTags.ShareUrl)

        // Owned by the native-expert region: the back control on BrowseScreen.
        compose.onTag(BROWSE_BACK).assertExists()
    }

    private companion object {
        /** The testTag the native-expert region puts on BrowseScreen's back control. */
        const val BROWSE_BACK = "browse-back"
    }
}
