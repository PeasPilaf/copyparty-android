package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The in-app browser promise: you always know which local address you are browsing, and you can go
 * back, reload or hand the page to a real browser. The WebView itself is not exercised here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BrowserChromeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the toolbar shows the address and offers back reload and open externally`() {
        var backs = 0
        var reloads = 0
        var externals = 0
        val url = "http://192.168.1.20:3923/"

        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                BrowserChrome(
                    url = url,
                    progress = 40,
                    onBack = { backs += 1 },
                    onReload = { reloads += 1 },
                    onOpenExternal = { externals += 1 },
                )
            }
        }

        compose.assertTextShown(url)
        compose.tap(UiTags.BrowserBack)
        compose.tap(UiTags.BrowserReload)
        compose.tap(UiTags.BrowserExternal)

        assertThat(backs).isEqualTo(1)
        assertThat(reloads).isEqualTo(1)
        assertThat(externals).isEqualTo(1)
    }
}
