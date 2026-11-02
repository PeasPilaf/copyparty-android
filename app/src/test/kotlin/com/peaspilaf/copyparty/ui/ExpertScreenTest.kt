package com.peaspilaf.copyparty.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.server.ServerStatus
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Expert mode is the second audience: someone who already knows copyparty gets the real thing. The
 * screen shows the exact `copyparty.conf` the app writes (copyable), lets them append raw global
 * directives and per-folder volflags, and hands them the full copyparty web UI instead of a subset.
 *
 * Nothing here is simulated ahead of the server: a value typed here goes through the same
 * [ConfigRenderer] and the same `AppViewModel.saveShare` / `updateSettings` the simple flow uses.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ExpertScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Back presses that reached the route above the expert tools, i.e. that would leave them. */
    private var outerBacks = 0

    private val alice = User("alice", "hunter2")

    private val photos = Share(
        id = "s-photos",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(Grant("alice", setOf(Permission.VIEW, Permission.UPLOAD))),
    )

    private val docs = Share(
        id = "s-docs",
        name = "Docs",
        urlPath = "docs",
        localPath = "/storage/docs",
        grants = listOf(Grant("alice", setOf(Permission.VIEW))),
    )

    private fun initialState(expertMode: Boolean = true, extraArgs: String = "") = AppState(
        users = listOf(alice),
        shares = listOf(photos, docs),
        settings = ServerSettings(
            port = 4040,
            serverName = "my phone",
            expertMode = expertMode,
            extraArgs = extraArgs,
        ),
    )

    private fun show(
        state: AppState = initialState(),
        server: FakeServerRuntime = FakeServerRuntime(ServerStatus.Stopped),
    ): FakeConfigStore {
        val store = FakeConfigStore(state)
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                // The screen this one was opened from: its back handler is the one that would close
                // the whole expert route.
                BackHandler { outerBacks += 1 }
                ExpertScreen(
                    viewModel = testViewModel(store = store, server = server),
                    onBack = {},
                )
            }
        }
        return store
    }

    @Test
    fun `expert mode shows the exact rendered config`() {
        val store = show()

        val shown = compose.onTag("expert-config")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

        // Not a summary and not a screenshot of a config: exactly what the app renders from the saved
        // state, produced by the same ConfigRenderer the server is started with.
        assertThat(shown).isEqualTo(ConfigRenderer.render(store.state))
    }

    @Test
    fun `extra global args are saved and appear in the rendered config`() {
        val store = show()

        compose.onTag("expert-args").performScrollTo().performTextReplacement("--dedup\n--no-dupe")
        compose.tap("expert-save-args")

        assertThat(store.state.settings.extraArgs).isEqualTo("--dedup\n--no-dupe")
        // The expert values are a change to the settings, not a replacement for the rest of them.
        assertThat(store.state.settings.expertMode).isTrue()
        assertThat(store.state.settings.port).isEqualTo(4040)
        assertThat(ConfigRenderer.render(store.state)).contains("  --dedup\n  --no-dupe\n")

        // The visible config updates too, so an expert sees what they just saved.
        compose.onTag("expert-config").assertTextContains("--no-dupe", substring = true)
    }

    @Test
    fun `per-share advanced flags are saved on that share only`() {
        val store = show()
        compose.onTag("expert-volumes").assertExists()

        // One flagged field per share, in the order the shares are listed.
        compose.onAllTags("expert-extra-flags")[1].performScrollTo().performTextReplacement("nodupe, nosub")
        compose.onAllTags("expert-save-flags")[1].performScrollTo().performClick()

        assertThat(store.state.shares.first { it.id == "s-docs" }.extraVolFlags).isEqualTo("nodupe, nosub")
        assertThat(store.state.shares.first { it.id == "s-photos" }.extraVolFlags).isEmpty()
        // Saving advanced flags edits a copy; the share's grants and folder are untouched.
        assertThat(store.state.shares.first { it.id == "s-docs" }.grants).isEqualTo(docs.grants)
        assertThat(store.state.shares.first { it.id == "s-docs" }.localPath).isEqualTo("/storage/docs")
    }

    @Test
    fun `the expert switch reflects the saved setting`() {
        val store = show(initialState(expertMode = true))
        compose.onTag("expert-mode").assertIsOn()

        compose.tap("expert-mode")

        assertThat(store.state.settings.expertMode).isFalse()
        compose.onTag("expert-mode").assertIsOff()
    }

    @Test
    fun `the rendered config can be copied`() {
        val store = show()

        compose.tap("expert-copy")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            .isEqualTo(ConfigRenderer.render(store.state))
    }

    @Test
    fun `the full copyparty UI waits for a running server`() {
        show(server = FakeServerRuntime(ServerStatus.Stopped))

        // No dead button: on a stopped server the screen says how to get an address instead.
        compose.onTag("expert-open-webui").assertIsNotEnabled()
        compose.assertTextShown("Start the server to open the full copyparty UI")
    }

    @Test
    fun `the full copyparty UI opens while the server runs`() {
        show(server = FakeServerRuntime(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923)))

        compose.onTag("expert-open-webui").assertIsEnabled()
        compose.tap("expert-open-webui")

        // It is copyparty's own web UI at the server root, not a reduced in-app copy.
        compose.onTag(UiTags.BrowserChrome).assertExists()
        compose.assertTextShown("http://127.0.0.1:3923")
    }

    @Test
    fun `system back leaves the copyparty web UI before it leaves the expert tools`() {
        show(server = FakeServerRuntime(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923)))
        compose.tap("expert-open-webui")
        compose.onTag(UiTags.BrowserChrome).assertExists()

        pressBack()

        // One step back: the expert tools are on screen again, and the route above was not closed.
        compose.onTag("expert-config").assertExists()
        assertThat(outerBacks).isEqualTo(0)

        // At the tools themselves, back is the normal one step out.
        pressBack()
        compose.waitUntil(5_000) { outerBacks == 1 }
    }

    @Test
    fun `the screen points at the copyparty control panel for everything else`() {
        // The one page copyparty serves at the server root IS the control panel for an admin account
        // (server config, recent uploads, settings), so the link to it is the full-UI button above.
        // The screen has to say so, and say that it needs an admin account, instead of inventing a
        // second address that would open the same page again.
        show()

        compose.assertTextShown("control panel")
        compose.assertTextShown("admin permission")
    }

    @Test
    fun `an expert edit on a running server says a restart is needed and the action restarts it`() {
        // The shipped lie this guards against: the screen said "saved" while the server kept serving
        // the old config. An argument that changes the config is saved, then honestly reported as not
        // live, and the one action offered is the restart that makes it live.
        val server = FakeServerRuntime(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
        show(server = server)

        compose.onTag("expert-args").performScrollTo().performTextReplacement("--dedup")
        compose.tap("expert-save-args")

        compose.assertTextShown("Changes are saved. Restart to apply.")
        compose.tap(UiTags.HomeRestartToApply)

        assertThat(server.applyCalls).isEqualTo(1)
        // The banner goes away because the restart was really handed to the runtime, not because a
        // message was dismissed.
        compose.waitUntil(5_000) {
            compose.onAllTags(UiTags.HomeRestartToApply).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
