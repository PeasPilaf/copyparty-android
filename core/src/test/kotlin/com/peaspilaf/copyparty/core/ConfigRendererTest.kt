package com.peaspilaf.copyparty.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfigRendererTest {
    private fun state(
        users: List<User> = listOf(User("alice", "hunter2")),
        shares: List<Share> = listOf(
            Share(
                id = "s1",
                name = "Photos",
                urlPath = "photos",
                localPath = "/storage/emulated/0/DCIM",
                grants = listOf(
                    Grant(Grant.EVERYONE, setOf(Permission.VIEW)),
                    Grant("alice", setOf(Permission.VIEW, Permission.UPLOAD)),
                ),
            ),
        ),
        settings: ServerSettings = ServerSettings(),
    ) = AppState(users = users, shares = shares, settings = settings)

    @Test
    fun `renders global section with plain http and the configured port`() {
        val conf = ConfigRenderer.render(state(settings = ServerSettings(port = 4242, lanAccess = false)))
        assertThat(conf).contains("[global]\n  p: 4242\n  i: 127.0.0.1\n")
        assertThat(conf).contains("  http-only\n")
        assertThat(conf).contains("  no-crt\n")
    }

    @Test
    fun `renders accounts and volume acl lines`() {
        val conf = ConfigRenderer.render(state())
        assertThat(conf).contains("[accounts]\n  alice: hunter2\n")
        assertThat(conf).contains("[/photos]\n  /storage/emulated/0/DCIM\n  accs:\n")
        assertThat(conf).contains("    r: *\n")
        assertThat(conf).contains("    rw: alice\n")
        assertThat(conf).contains("  flags:\n    e2d\n")
    }

    @Test
    fun `a readable user does not also get the get-only flag`() {
        assertThat(ConfigRenderer.accsFlags(setOf(Permission.VIEW, Permission.DOWNLOAD))).isEqualTo("r")
        assertThat(ConfigRenderer.accsFlags(setOf(Permission.DOWNLOAD))).isEqualTo("g")
        assertThat(ConfigRenderer.accsFlags(setOf(Permission.UPLOAD, Permission.DELETE))).isEqualTo("wd")
    }

    @Test
    fun `disabled shares are not rendered`() {
        val conf = ConfigRenderer.render(state(shares = listOf(Share("s1", "x", "x", "/tmp/x", enabled = false))))
        assertThat(conf).doesNotContain("[/x]")
    }

    @Test
    fun `rendering is deterministic`() {
        val rendered = ConfigRenderer.render(state())
        assertThat(ConfigRenderer.render(state())).isEqualTo(rendered)
    }
}
