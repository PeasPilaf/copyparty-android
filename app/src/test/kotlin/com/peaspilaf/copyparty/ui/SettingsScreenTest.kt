package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Settings screen promise: the handful of knobs that change how the server behaves are edited
 * in one form and saved as one change. A value the server could not start with is pointed at and
 * never written to disk.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val alice = User("alice", "hunter2")

    private fun openSettings(): FakeConfigStore {
        val store = FakeConfigStore(
            AppState(
                users = listOf(alice),
                shares = listOf(
                    Share(
                        id = "s1",
                        name = "Photos",
                        urlPath = "photos",
                        localPath = "/storage/photos",
                        grants = listOf(Grant("alice", setOf(Permission.VIEW))),
                    ),
                ),
            ),
        )
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store))
            }
        }
        compose.tap(UiTags.NavSettings)
        return store
    }

    @Test
    fun `every edited setting is saved in one go`() {
        val store = openSettings()

        compose.onTag(UiTags.SettingsPort).performTextReplacement("4000")
        compose.tap(UiTags.SettingsLanAccess)
        compose.tap(UiTags.SettingsStartOnBoot)
        compose.onTag(UiTags.SettingsServerName).performTextReplacement("my phone")
        compose.tap(UiTags.ThemeDark)
        compose.tap(UiTags.SettingsSave)

        assertThat(store.state.settings).isEqualTo(
            ServerSettings(
                port = 4000,
                lanAccess = false,
                startOnBoot = true,
                serverName = "my phone",
                theme = ThemeMode.DARK,
            ),
        )
    }

    @Test
    fun `an invalid port is explained and never saved`() {
        val store = openSettings()

        compose.onTag(UiTags.SettingsPort).performTextReplacement("80")
        compose.tap(UiTags.SettingsSave)

        compose.onTag(UiTags.SettingsErrors).assertExists()
        compose.assertTextShown("Port must be between")
        assertThat(store.saves).isEmpty()
    }
}
