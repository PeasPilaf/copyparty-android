package com.peaspilaf.copyparty.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The JSON file is the only copy of a person's accounts and shares, so it has to survive the app
 * being updated: everything they configured comes back unchanged, and keys written by a newer
 * version never stop an older one from loading the file.
 */
class ModelSerializationTest {
    // The same settings JsonConfigStore in :app uses; :core cannot depend on it.
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val state = AppState(
        users = listOf(User("alice", "hunter2"), User("bob", "letmein")),
        shares = listOf(
            Share(
                id = "s-photos",
                name = "Photos",
                urlPath = "photos",
                localPath = "/storage/emulated/0/DCIM",
                grants = listOf(
                    Grant(Grant.EVERYONE, setOf(Permission.VIEW)),
                    Grant(
                        "alice",
                        setOf(
                            Permission.VIEW,
                            Permission.DOWNLOAD,
                            Permission.UPLOAD,
                            Permission.MOVE,
                            Permission.DELETE,
                            Permission.ADMIN,
                        ),
                    ),
                ),
            ),
            Share(
                id = "s-notes",
                name = "Notes",
                urlPath = "notes",
                localPath = "/storage/emulated/0/Documents",
                grants = listOf(
                    Grant("bob", setOf(Permission.DOWNLOAD, Permission.UPLOAD)),
                    Grant(Grant.EVERYONE, emptySet()),
                ),
                enabled = false,
            ),
        ),
        settings = ServerSettings(
            port = 4242,
            lanAccess = false,
            startOnBoot = true,
            serverName = "my phone",
            theme = ThemeMode.DARK,
        ),
    )

    @Test
    fun `the whole state survives a json round trip`() {
        val encoded = json.encodeToString(AppState.serializer(), state)
        val decoded = json.decodeFromString(AppState.serializer(), encoded)
        assertThat(decoded).isEqualTo(state)
    }

    @Test
    fun `permissions and settings are written by name so the file stays readable`() {
        val encoded = json.encodeToString(AppState.serializer(), state)
        assertThat(encoded).contains("\"permissions\"")
        assertThat(encoded).contains("\"VIEW\"")
        assertThat(encoded).contains("\"DARK\"")
        assertThat(encoded).contains("\"version\"")
        assertThat(encoded).contains("4242")
    }

    @Test
    fun `unknown json keys written by a newer app are ignored`() {
        val fromNewerVersion = """
            {
              "version": 2,
              "users": [
                {"name": "alice", "password": "hunter2", "lastSeen": "2026-01-01"}
              ],
              "shares": [
                {
                  "id": "s-photos",
                  "name": "Photos",
                  "urlPath": "photos",
                  "localPath": "/storage/emulated/0/DCIM",
                  "grants": [
                    {"principal": "*", "permissions": ["VIEW", "UPLOAD"], "expiresAt": 1893456000}
                  ],
                  "sortOrder": 3
                }
              ],
              "settings": {"port": 3923, "serverName": "copyparty", "theme": "SYSTEM", "autoRestart": true},
              "uiState": {"lastTab": "shares"}
            }
        """.trimIndent()

        val decoded = json.decodeFromString(AppState.serializer(), fromNewerVersion)

        assertThat(decoded.users).containsExactly(User("alice", "hunter2"))
        assertThat(decoded.shares).containsExactly(
            Share(
                id = "s-photos",
                name = "Photos",
                urlPath = "photos",
                localPath = "/storage/emulated/0/DCIM",
                grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW, Permission.UPLOAD))),
            ),
        )
        assertThat(decoded.settings).isEqualTo(ServerSettings())
    }

    @Test
    fun `a share written without grants or the enabled flag loads with its defaults`() {
        val olderFile = """
            {"shares": [{"id": "s1", "name": "Photos", "urlPath": "photos", "localPath": "/tmp/photos"}]}
        """.trimIndent()

        val decoded = json.decodeFromString(AppState.serializer(), olderFile)

        assertThat(decoded.shares.single().grants).isEmpty()
        assertThat(decoded.shares.single().enabled).isTrue()
        assertThat(decoded.settings).isEqualTo(ServerSettings())
        assertThat(decoded.version).isEqualTo(1)
    }
}
