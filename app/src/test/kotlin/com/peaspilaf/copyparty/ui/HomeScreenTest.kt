package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Home screen promise: one glance tells you whether this phone is serving files, one tap starts
 * or stops it, and a configuration that cannot work is explained before you try to start it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(viewModel: AppViewModel) {
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(viewModel)
            }
        }
    }

    @Test
    fun `a stopped server offers a start control`() {
        show(testViewModel(server = FakeServerRuntime(ServerStatus.Stopped)))

        compose.onTag(UiTags.StartServer).assertIsDisplayed()
    }

    @Test
    fun `tapping start asks the runtime to start the server`() {
        val server = FakeServerRuntime(ServerStatus.Stopped)
        show(testViewModel(server = server))

        compose.tap(UiTags.StartServer)

        assertThat(server.startCalls).isEqualTo(1)
    }

    @Test
    fun `a running server shows its url and a stop control`() {
        val url = "http://192.168.1.20:3923"
        show(testViewModel(server = FakeServerRuntime(ServerStatus.Running(listOf(url), 3923))))

        compose
            .onNode(hasTestTag(UiTags.ServerUrl) and hasText(url, substring = true), useUnmergedTree = true)
            .assertExists()
        compose.onTag(UiTags.StopServer).assertIsDisplayed()
    }

    @Test
    fun `a failed server shows the error message`() {
        show(testViewModel(server = FakeServerRuntime(ServerStatus.Failed("port 3923 is already in use"))))

        compose.assertTextShown("port 3923 is already in use")
    }

    @Test
    fun `validation errors are shown and stop the server from being started`() {
        show(testViewModel(store = FakeConfigStore(AppState(settings = ServerSettings(port = 80)))))

        compose.onTag(UiTags.ValidationBanner).assertIsDisplayed()
        compose.assertTextShown("Port must be between")
        compose.onTag(UiTags.StartServer).assertIsNotEnabled()
    }
}
