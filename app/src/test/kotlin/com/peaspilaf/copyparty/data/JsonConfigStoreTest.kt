package com.peaspilaf.copyparty.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.User
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The user's folders, accounts and settings live in one JSON file. These promises make that file
 * safe to lose power on mid-write, safe to save over and over, readable by a human who wants to
 * inspect or repair it, and tolerant of a state file written by a newer app version.
 */
class JsonConfigStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store(file: File = File(temp.root, "state.json")) = JsonConfigStore(file)

    @Test
    fun `a missing file loads an empty state`() {
        assertThat(store().load()).isEqualTo(AppState())
    }

    @Test
    fun `state survives a save and load round trip`() {
        val file = File(temp.root, "state.json")
        val state = AppState(
            users = listOf(User("alice", "hunter2")),
            shares = listOf(
                Share(
                    id = "s1",
                    name = "Photos",
                    urlPath = "photos",
                    localPath = "/tmp/photos",
                    grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW, Permission.UPLOAD))),
                ),
            ),
            settings = ServerSettings(port = 4242, lanAccess = false),
        )
        store(file).save(state)
        assertThat(store(file).load()).isEqualTo(state)
    }

    @Test
    fun `corrupt json falls back to an empty state instead of crashing`() {
        val file = File(temp.root, "state.json")
        file.writeText("{not json")
        assertThat(store(file).load()).isEqualTo(AppState())
    }

    @Test
    fun `saving leaves no temporary file next to the state file`() {
        val file = File(temp.root, "state.json")

        store(file).save(AppState(users = listOf(User("alice", "hunter2"))))

        assertThat(file.isFile).isTrue()
        assertThat(temp.root.listFiles().orEmpty().map { it.name }).containsExactly("state.json")
    }

    @Test
    fun `saving again replaces the previous state completely`() {
        val file = File(temp.root, "state.json")
        store(file).save(
            AppState(users = listOf(User("alice", "hunter2")), settings = ServerSettings(port = 3923)),
        )
        val latest = AppState(
            users = listOf(User("bob", "correct horse")),
            shares = listOf(Share("s1", "Photos", "photos", "/tmp/photos")),
            settings = ServerSettings(port = 4040, lanAccess = false),
        )

        store(file).save(latest)

        assertThat(store(file).load()).isEqualTo(latest)
        assertThat(temp.root.listFiles().orEmpty().map { it.name }).containsExactly("state.json")
    }

    @Test
    fun `the state file is indented json a person can read`() {
        val file = File(temp.root, "state.json")
        store(file).save(
            AppState(
                users = listOf(User("alice", "hunter2")),
                shares = listOf(Share("s1", "Photos", "photos", "/tmp/photos")),
            ),
        )

        val lines = file.readText().lines().filter { it.isNotBlank() }

        assertThat(lines.size).isAtLeast(8)
        assertThat(lines.first()).isEqualTo("{")
        assertThat(lines.last()).isEqualTo("}")
        assertThat(lines.any { it.trim() == "\"users\": [" }).isTrue()
        for (line in lines.drop(1).dropLast(1)) {
            assertWithMessage("expected an indented line but got: $line").that(line.startsWith(" ")).isTrue()
        }
    }

    @Test
    fun `load ignores fields written by a newer app version`() {
        val file = File(temp.root, "state.json")
        file.writeText(
            """
            {
              "version": 7,
              "futureOption": {"enabled": true},
              "users": [{"name": "alice", "password": "hunter2", "nickname": "al"}],
              "shares": []
            }
            """.trimIndent(),
        )

        val loaded = store(file).load()

        assertThat(loaded.version).isEqualTo(7)
        assertThat(loaded.users).containsExactly(User("alice", "hunter2"))
        assertThat(loaded.shares).isEmpty()
        assertThat(loaded.settings).isEqualTo(ServerSettings())
    }
}
