package com.peaspilaf.copyparty.ui

import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.server.ServerStatus
import org.junit.Test

/**
 * The ViewModel promise: every edit a screen can make is one state change that is written to the
 * store immediately, and the generated defaults (link paths, passwords) are predictable enough to
 * rely on.
 */
class AppViewModelTest {

    private val alice = User("alice", "hunter2")
    private val bob = User("bob", "hunter3")

    private fun viewModel(initial: AppState = AppState()): AppViewModel =
        AppViewModel(FakeConfigStore(initial), FakeServerRuntime(), FakeStorageAccess())

    private fun share(id: String, urlPath: String, grants: List<Grant> = emptyList()): Share = Share(
        id = id,
        name = urlPath,
        urlPath = urlPath,
        localPath = "/storage/$urlPath",
        grants = grants,
    )

    @Test
    fun `deleting the last share while running stops the server instead of leaving it stale`() {
        val server = FakeServerRuntime(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
        val vm = AppViewModel(
            FakeConfigStore(
                AppState(
                    shares = listOf(share("s1", "photos", listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))))),
                ),
            ),
            server,
            FakeStorageAccess(),
        )

        vm.deleteShare("s1")

        assertThat(vm.pendingApply.value).isTrue()
        assertThat(vm.applyIfSupported()).isEqualTo(ServerStatus.Stopped)
        assertThat(server.stopCalls).isEqualTo(1)
        assertThat(server.applyCalls).isEqualTo(0)
        assertThat(vm.pendingApply.value).isFalse()
    }

    @Test
    fun `applying a configuration the server cannot use reports why and touches nothing`() {
        val server = FakeServerRuntime(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
        val vm = AppViewModel(
            FakeConfigStore(
                AppState(
                    // A privileged port is a validator ERROR, so this config can never be applied.
                    settings = ServerSettings(port = 80),
                    shares = listOf(share("s1", "photos", listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))))),
                ),
            ),
            server,
            FakeStorageAccess(),
        )

        val result = vm.applyIfSupported()

        assertThat(result).isInstanceOf(ServerStatus.Failed::class.java)
        assertThat(vm.applyError.value).isNotNull()
        assertThat(server.applyCalls).isEqualTo(0)
    }

    @Test
    fun `revoking a person while running is saved but not live until a restart`() {
        val server = FakeServerRuntime(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
        val vm = AppViewModel(
            FakeConfigStore(
                AppState(
                    users = listOf(alice),
                    shares = listOf(share("s1", "photos", listOf(Grant("alice", setOf(Permission.VIEW))))),
                ),
            ),
            server,
            FakeStorageAccess(),
        )

        vm.deleteUser("alice")

        assertThat(vm.pendingApply.value).isTrue()
        assertThat(vm.state.value.users).isEmpty()
    }

    @Test
    fun `suggest url path turns a folder name into a lowercase dash slug`() {
        val vm = viewModel()

        assertThat(vm.suggestUrlPath("My Holiday Photos")).isEqualTo("my-holiday-photos")
        assertThat(vm.suggestUrlPath("  A/B  ")).isEqualTo("a-b")
        assertThat(vm.suggestUrlPath("***")).isEqualTo("share")
    }

    @Test
    fun `suggest url path adds a numeric suffix when the slug is taken`() {
        val vm = viewModel(
            AppState(shares = listOf(share("s1", "photos"), share("s2", "photos-2"))),
        )

        assertThat(vm.suggestUrlPath("Photos")).isEqualTo("photos-3")
        assertThat(vm.suggestUrlPath("PHOTOS")).isEqualTo("photos-3")
    }

    @Test
    fun `random passwords have the requested length and avoid lookalike characters`() {
        val vm = viewModel()

        val password = vm.randomPassword(24)

        assertThat(password).hasLength(24)
        assertThat(password).matches("[A-Za-z0-9]{24}")
        assertThat(password).doesNotContain("l")
        assertThat(password).doesNotContain("1")
        assertThat(password).doesNotContain("0")
        assertThat(password).doesNotContain("O")
        assertThat(vm.randomPassword()).hasLength(16)
    }

    @Test
    fun `deleting a user strips their grants from every share`() {
        val store = FakeConfigStore(
            AppState(
                users = listOf(alice, bob),
                shares = listOf(
                    share(
                        "s1",
                        "photos",
                        grants = listOf(
                            Grant("alice", setOf(Permission.VIEW)),
                            Grant("bob", setOf(Permission.UPLOAD)),
                        ),
                    ),
                    share(
                        "s2",
                        "docs",
                        grants = listOf(
                            Grant("alice", setOf(Permission.DOWNLOAD)),
                            Grant(Grant.EVERYONE, setOf(Permission.VIEW)),
                        ),
                    ),
                ),
            ),
        )
        val vm = AppViewModel(store, FakeServerRuntime(), FakeStorageAccess())

        vm.deleteUser("alice")

        assertThat(vm.state.value.users).containsExactly(bob)
        assertThat(vm.state.value.shares[0].grants)
            .containsExactly(Grant("bob", setOf(Permission.UPLOAD)))
        assertThat(vm.state.value.shares[1].grants)
            .containsExactly(Grant(Grant.EVERYONE, setOf(Permission.VIEW)))
        assertThat(store.state).isEqualTo(vm.state.value)
    }

    @Test
    fun `saving a share with a known id replaces it in place`() {
        val vm = viewModel(
            AppState(shares = listOf(share("s1", "photos"), share("s2", "docs"))),
        )

        vm.saveShare(share("s1", "renamed"))

        assertThat(vm.state.value.shares.map { it.id }).containsExactly("s1", "s2").inOrder()
        assertThat(vm.state.value.shares[0].urlPath).isEqualTo("renamed")
    }

    @Test
    fun `saving a share with a new id appends it`() {
        val vm = viewModel(AppState(shares = listOf(share("s1", "photos"))))

        vm.saveShare(share("s2", "docs"))

        assertThat(vm.state.value.shares.map { it.id }).containsExactly("s1", "s2").inOrder()
    }

    @Test
    fun `every change is written through to the store at once`() {
        val store = FakeConfigStore()
        val vm = AppViewModel(store, FakeServerRuntime(), FakeStorageAccess())
        val settings = ServerSettings(port = 4000, lanAccess = false, serverName = "my phone")

        vm.updateSettings(settings)

        assertThat(store.saves).hasSize(1)
        assertThat(store.state.settings).isEqualTo(settings)
        assertThat(vm.state.value.settings).isEqualTo(settings)
    }
}
