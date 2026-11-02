package com.peaspilaf.copyparty.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The server-log promise: the file the embedded server writes is reachable from the app, so a
 * server that will not start can be diagnosed without a laptop and a USB cable.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ServerLogTest {

    @get:Rule
    val compose = createComposeRule()

    private val url = "http://192.168.1.20:3923"

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

    @Test
    fun `the home screen opens the log the server writes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The runtime writes copyparty's log next to its config, inside the app's own storage.
        val log = File(context.filesDir, "copyparty/copyparty.log")
        log.parentFile?.mkdirs()
        log.writeText("copyparty starting on port 3923\nlistening on 0.0.0.0:3923\n")

        val viewModel = testViewModel(
            store = FakeConfigStore(AppState(shares = listOf(photos))),
            server = FakeServerRuntime(ServerStatus.Running(listOf(url), 3923)),
        )
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(viewModel)
            }
        }

        compose.tap(UiTags.HomeServerLog)

        compose.assertTextShown("listening on 0.0.0.0:3923")
    }
}
