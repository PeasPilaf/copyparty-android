package com.peaspilaf.copyparty.ui

import android.Manifest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
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

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
    )

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

        compose.onTag(UiTags.HomeStart).assertIsDisplayed()
    }

    @Test
    fun `tapping start asks the runtime to start the server`() {
        val server = FakeServerRuntime(ServerStatus.Stopped)
        show(testViewModel(store = FakeConfigStore(AppState(shares = listOf(photos))), server = server))

        compose.tap(UiTags.HomeStart)

        assertThat(server.startCalls).isEqualTo(1)
    }

    @Test
    fun `a running server shows its url and a stop control`() {
        val url = "http://192.168.1.20:3923"
        show(testViewModel(server = FakeServerRuntime(ServerStatus.Running(listOf(url), 3923))))

        compose
            .onNode(hasTestTag(UiTags.HomeAddress) and hasText(url, substring = true), useUnmergedTree = true)
            .assertExists()
        compose.onTag(UiTags.HomeStop).assertIsDisplayed()
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
        compose.onTag(UiTags.HomeStart).assertIsNotEnabled()
    }

    @Test
    fun `starting the server asks to show the server notification`() {
        // From Android 13 a foreground service is silent unless POST_NOTIFICATIONS was asked for, so
        // the start button is the one control that must ask (`rememberNotificationPermissionRequester`).
        val requested = mutableListOf<String>()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                if (contract is ActivityResultContracts.RequestPermission) requested += input as String
            }
        }
        val server = FakeServerRuntime(ServerStatus.Stopped)
        val viewModel = testViewModel(
            store = FakeConfigStore(AppState(shares = listOf(photos))),
            server = server,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry: ActivityResultRegistry = registry
                },
            ) {
                CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                    AppRoot(viewModel)
                }
            }
        }

        compose.tap(UiTags.HomeStart)

        assertThat(requested).containsExactly(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(server.startCalls).isEqualTo(1)
    }
}
