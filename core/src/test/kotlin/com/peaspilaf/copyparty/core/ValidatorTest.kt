package com.peaspilaf.copyparty.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Says exactly what the app refuses to save and what it only warns about.
 *
 * Every [Issue] carries the path of the field it belongs to, so the screen can show the message next
 * to the input; these tests pin severity and path, not the human wording of the message.
 */
class ValidatorTest {
    /** The smallest state that is good enough to start the server: one account, one public folder. */
    private fun validState() = AppState(
        users = listOf(User("alice", "hunter2")),
        shares = listOf(
            Share(
                id = "s-photos",
                name = "Photos",
                urlPath = "photos",
                localPath = "/storage/emulated/0/DCIM",
                grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
            ),
        ),
    )

    private fun errors(state: AppState) = Validator.validate(state).filter { it.severity == Severity.ERROR }

    private fun warnings(state: AppState) = Validator.validate(state).filter { it.severity == Severity.WARNING }

    private fun paths(issues: List<Issue>) = issues.map { it.path }

    @Test
    fun `a valid state has no issues`() {
        assertThat(Validator.validate(validState())).isEmpty()
    }

    @Test
    fun `a port outside 1024 to 65535 is an error on settings port`() {
        for (port in listOf(Validator.MIN_PORT - 1, Validator.MAX_PORT + 1)) {
            val state = validState().copy(settings = ServerSettings(port = port))
            assertThat(paths(errors(state))).containsExactly("settings.port")
        }
        for (port in listOf(Validator.MIN_PORT, Validator.MAX_PORT, 3923)) {
            val state = validState().copy(settings = ServerSettings(port = port))
            assertThat(errors(state)).isEmpty()
        }
    }

    @Test
    fun `a server name copyparty cannot print is an error on settings server name`() {
        for (name in listOf("bad/name", "bad:name", "bad\u0007name", "a".repeat(65))) {
            val state = validState().copy(settings = ServerSettings(serverName = name))
            assertThat(paths(errors(state))).containsExactly("settings.serverName")
        }
        assertThat(errors(validState().copy(settings = ServerSettings(serverName = "my phone")))).isEmpty()
    }

    @Test
    fun `a user name with spaces is an error on that user`() {
        val state = validState().copy(users = listOf(User("bad name", "hunter2")))
        assertThat(paths(errors(state))).containsExactly("users[0].name")
    }

    @Test
    fun `two users with the same name are an error on the second one`() {
        val state = validState().copy(users = listOf(User("alice", "hunter2"), User("alice", "hunter3")))
        assertThat(paths(errors(state))).containsExactly("users[1].name")
    }

    @Test
    fun `an empty password is an error on that user`() {
        val state = validState().copy(users = listOf(User("alice", "")))
        assertThat(paths(errors(state))).containsExactly("users[0].password")
    }

    @Test
    fun `a password with a colon or a line break is an error on that user`() {
        for (password in listOf("hunter:2", "hunter\n2", "hunter\r2")) {
            val state = validState().copy(users = listOf(User("alice", password)))
            assertThat(paths(errors(state))).containsExactly("users[0].password")
        }
    }

    @Test
    fun `a state without an enabled share is an error`() {
        val disabled = validState().shares[0].copy(enabled = false)
        val state = validState().copy(shares = listOf(disabled))
        assertThat(paths(errors(state))).containsExactly("shares")

        // A brand new install has nothing to serve yet.
        assertThat(paths(errors(AppState()))).containsExactly("shares")
    }

    @Test
    fun `a link path with characters copyparty cannot mount is an error on that share`() {
        val share = validState().shares[0].copy(urlPath = "my photos")
        val state = validState().copy(shares = listOf(share))
        assertThat(paths(errors(state))).containsExactly("shares[0].urlPath")
    }

    @Test
    fun `two shares on the same link path are an error on the second one`() {
        for (secondUrlPath in listOf("photos", "Photos")) {
            val first = validState().shares[0]
            val state = validState().copy(
                shares = listOf(first, first.copy(id = "s-more", name = "More photos", urlPath = secondUrlPath)),
            )
            assertThat(paths(errors(state))).containsExactly("shares[1].urlPath")
        }
    }

    @Test
    fun `a grant for a user who does not exist is an error on that grant`() {
        val share = validState().shares[0].copy(
            grants = listOf(
                Grant(Grant.EVERYONE, setOf(Permission.VIEW)),
                Grant("carol", setOf(Permission.VIEW)),
            ),
        )
        val state = validState().copy(shares = listOf(share))
        assertThat(paths(errors(state))).containsExactly("shares[0].grants[1].principal")
    }

    @Test
    fun `a grant without permissions is only a warning on that grant`() {
        val share = validState().shares[0].copy(
            grants = listOf(
                Grant("alice", emptySet()),
                Grant(Grant.EVERYONE, setOf(Permission.VIEW)),
            ),
        )
        val state = validState().copy(shares = listOf(share))

        assertThat(errors(state)).isEmpty()
        assertThat(paths(warnings(state))).containsExactly("shares[0].grants[0].permissions")
    }

    @Test
    fun `move or delete without upload is only a warning on that grant`() {
        for (permissions in listOf(
            setOf(Permission.MOVE),
            setOf(Permission.DELETE),
            setOf(Permission.VIEW, Permission.MOVE),
        )) {
            val share = validState().shares[0].copy(grants = listOf(Grant("alice", permissions)))
            val state = validState().copy(shares = listOf(share))
            assertThat(errors(state)).isEmpty()
            assertThat(paths(warnings(state))).containsExactly("shares[0].grants[0].permissions")
        }

        val uploadToo = validState().shares[0].copy(
            grants = listOf(Grant("alice", setOf(Permission.UPLOAD, Permission.MOVE, Permission.DELETE))),
        )
        assertThat(warnings(validState().copy(shares = listOf(uploadToo)))).isEmpty()
    }

    @Test
    fun `a relative folder path is an error on that share`() {
        val share = validState().shares[0].copy(localPath = "storage/emulated/0/DCIM")
        val state = validState().copy(shares = listOf(share))
        assertThat(paths(errors(state))).containsExactly("shares[0].localPath")
    }

    // -- Expert mode ---------------------------------------------------------------------------
    // Expert mode hands the user raw copyparty config lines, so the Validator is the guard that a
    // pasted line cannot start a new section and change an unrelated part of the file.

    @Test
    fun `a section header in expert args is refused`() {
        for (args in listOf("[evil]", "  [evil]", "--dedup\n[evil]")) {
            val state = validState().copy(settings = ServerSettings(extraArgs = args))
            assertThat(paths(errors(state))).contains("settings.extraArgs")
        }
    }

    @Test
    fun `a control character in expert args is refused`() {
        for (args in listOf("--dedup\u0007", "--dedup\n\u0000no-dupe")) {
            val state = validState().copy(settings = ServerSettings(extraArgs = args))
            assertThat(paths(errors(state))).contains("settings.extraArgs")
        }
    }

    @Test
    fun `a section header or a line break in advanced folder flags is refused`() {
        for (flags in listOf("[evil]", "nodupe\n[evil]", "e2d\u0000")) {
            val share = validState().shares[0].copy(extraVolFlags = flags)
            val state = validState().copy(shares = listOf(share))
            assertThat(paths(errors(state))).contains("shares[0].extraVolFlags")
        }
    }

    @Test
    fun `ordinary expert values are accepted`() {
        val state = validState().copy(
            settings = ServerSettings(extraArgs = "--dedup\n--no-dupe"),
            shares = listOf(validState().shares[0].copy(extraVolFlags = "nodupe, nosub")),
        )

        assertThat(Validator.validate(state)).isEmpty()
    }
}
