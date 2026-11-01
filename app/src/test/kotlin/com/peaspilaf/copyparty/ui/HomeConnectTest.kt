package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The "get connected" promise: a running server offers the in-app browser, a QR code and a copy
 * action; a stopped server never shows controls that cannot work.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeConnectTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        server: FakeServerRuntime,
        onOpenBrowser: (String) -> Unit = {},
        onShowQr: (String) -> Unit = {},
    ): AppViewModel {
        val viewModel = testViewModel(server = server)
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(viewModel, viewModel.state.value, onOpenBrowser, onShowQr)
            }
        }
        return viewModel
    }

    @Test
    fun `a running server offers the browser the qr code and the address to copy`() {
        val url = "http://192.168.1.20:3923"
        var opened: String? = null
        var qr: String? = null
        show(
            server = FakeServerRuntime(ServerStatus.Running(listOf(url), 3923)),
            onOpenBrowser = { opened = it },
            onShowQr = { qr = it },
        )

        compose.onTag(UiTags.OpenBrowser).assertIsDisplayed()
        compose.onTag(UiTags.ShowQr).assertIsDisplayed()
        compose.onTag(UiTags.CopyUrl).assertIsDisplayed()

        compose.tap(UiTags.OpenBrowser)
        compose.tap(UiTags.ShowQr)

        assertThat(opened).isEqualTo(url)
        assertThat(qr).isEqualTo(url)
    }

    @Test
    fun `a stopped server offers no connection shortcuts`() {
        show(FakeServerRuntime(ServerStatus.Stopped))

        compose.onTag(UiTags.OpenBrowser).assertDoesNotExist()
        compose.onTag(UiTags.ShowQr).assertDoesNotExist()
        compose.onTag(UiTags.CopyUrl).assertDoesNotExist()
    }
}
