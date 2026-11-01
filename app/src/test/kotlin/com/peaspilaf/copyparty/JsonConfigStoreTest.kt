package com.peaspilaf.copyparty

import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.data.JsonConfigStore
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
}
