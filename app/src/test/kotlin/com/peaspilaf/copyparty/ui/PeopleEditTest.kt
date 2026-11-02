package com.peaspilaf.copyparty.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The people management promise: a generated password is one tap away and masked until asked for,
 * a person can be renamed or given a new password without losing what they could already open, and
 * removing someone names what they lose and can be undone from the same screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PeopleEditTest {

    @get:Rule
    val compose = createComposeRule()

    private val alice = User("alice", "hunter2")
    private val bob = User("bob", "hunter3")

    private val photos = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(
            Grant("alice", setOf(Permission.VIEW)),
            Grant("bob", setOf(Permission.UPLOAD)),
        ),
    )

    private fun show(state: AppState): FakeConfigStore {
        val store = FakeConfigStore(state)
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store))
            }
        }
        compose.tap(UiTags.NavPeople)
        return store
    }

    private fun typedPassword(): String = compose
        .onTag(UiTags.PersonPassword)
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    @Test
    fun `the generate button fills a strong generated password`() {
        show(AppState(users = listOf(alice), shares = listOf(photos)))

        compose.tap(UiTags.AddPerson)
        compose.tap(UiTags.PersonGenerate)

        val generated = typedPassword()
        assertThat(generated).isNotEmpty()
        assertThat(generated.length).isAtLeast(12)
        assertThat(generated).matches("[A-Za-z0-9]+")
    }

    @Test
    fun `the password is masked until revealed`() {
        show(AppState(users = listOf(alice), shares = listOf(photos)))

        compose.tap(UiTags.AddPerson)
        compose.onTag(UiTags.PersonPassword)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

        compose.tap(UiTags.PersonPasswordReveal)

        compose.onTag(UiTags.PersonPassword)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
    }

    @Test
    fun `renaming a person updates the shares that named them`() {
        val store = show(AppState(users = listOf(alice, bob), shares = listOf(photos)))

        compose.tapInRow(UiTags.PersonRow, "alice", UiTags.PersonEdit)
        compose.onTag(UiTags.PersonNameField).performTextReplacement("alicia")
        compose.tap(UiTags.PersonSave)

        assertThat(store.state.users.map { it.name }).containsExactly("alicia", "bob")
        assertThat(store.state.shares.single().grants)
            .containsExactly(
                Grant("alicia", setOf(Permission.VIEW)),
                Grant("bob", setOf(Permission.UPLOAD)),
            )
    }

    @Test
    fun `editing a person's password keeps every grant`() {
        val store = show(AppState(users = listOf(alice, bob), shares = listOf(photos)))

        compose.tapInRow(UiTags.PersonRow, "alice", UiTags.PersonEdit)
        compose.tap(UiTags.PersonGenerate)
        compose.tap(UiTags.PersonSave)

        assertThat(store.state.users.first { it.name == "alice" }.password).isNotEqualTo("hunter2")
        assertThat(store.state.shares.single().grants)
            .containsExactly(
                Grant("alice", setOf(Permission.VIEW)),
                Grant("bob", setOf(Permission.UPLOAD)),
            )
    }

    @Test
    fun `deleting a person names the shares that lose access and offers undo`() {
        val store = show(AppState(users = listOf(alice, bob), shares = listOf(photos)))

        compose.tap(UiTags.deletePerson("alice"))
        compose.onDialogContaining(UiTags.DeletePersonDialog, "Photos").assertExists()
        compose.onDialogContaining(UiTags.DeletePersonDialog, "lose access").assertExists()

        compose.tap(UiTags.DeletePersonConfirm)

        assertThat(store.state.users.map { it.name }).containsExactly("bob")
        assertThat(store.state.shares.single().grants)
            .containsExactly(Grant("bob", setOf(Permission.UPLOAD)))

        compose.awaitText("Undo")
        compose.onNode(hasText("Undo") and hasClickAction()).performClick()
        compose.waitForIdle()

        assertThat(store.state.users.map { it.name }).containsExactly("alice", "bob")
        assertThat(store.state.shares.single().grants)
            .containsExactly(
                Grant("alice", setOf(Permission.VIEW)),
                Grant("bob", setOf(Permission.UPLOAD)),
            )
    }

    @Test
    fun `the people empty state offers to add the first person`() {
        show(AppState(users = emptyList(), shares = listOf(photos)))

        compose.onTag(UiTags.EmptyAddPerson).assertExists()
        compose.tap(UiTags.EmptyAddPerson)

        compose.onTag(UiTags.PersonDialog).assertExists()
    }
}
