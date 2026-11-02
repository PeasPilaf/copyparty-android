package com.peaspilaf.copyparty.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
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
import org.robolectric.annotation.Config

/**
 * The "get connected" promise: a running server offers the in-app browser, a QR code and a copy
 * action, copying says so on screen instead of silently doing something, and a stopped server never
 * shows a control that cannot work.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeConnectTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val url = "http://192.168.1.20:3923"

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    private fun show(server: FakeServerRuntime) {
        val viewModel = testViewModel(
            store = FakeConfigStore(AppState(shares = listOf(photos))),
            server = server,
        )
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(viewModel)
            }
        }
    }

    private fun clipboard(): ClipboardManager =
        requireNotNull(compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)

    @Test
    fun `a running server offers the browser the qr code and the address to copy`() {
        show(FakeServerRuntime(ServerStatus.Running(listOf(url), 3923)))

        compose.onTag(UiTags.HomeBrowse).assertExists()
        compose.onTag(UiTags.HomeQr).assertExists()
        compose.onTag(UiTags.HomeCopy).assertExists()
        compose.onTag(UiTags.HomeAddress).assertExists()
        compose.assertTextShown(url)
    }

    @Test
    fun `a stopped server offers no copy, browse or QR control`() {
        show(FakeServerRuntime(ServerStatus.Stopped))

        compose.onTag(UiTags.HomeCopy).assertDoesNotExist()
        compose.onTag(UiTags.HomeBrowse).assertDoesNotExist()
        compose.onTag(UiTags.HomeQr).assertDoesNotExist()
    }

    @Test
    fun `copying the address confirms on screen`() {
        show(FakeServerRuntime(ServerStatus.Running(listOf(url), 3923)))

        compose.tap(UiTags.HomeCopy)

        compose.awaitText("copied")
        assertThat(clipboard().primaryClip?.getItemAt(0)?.text?.toString()).isEqualTo(url)
    }
}
