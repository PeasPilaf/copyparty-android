package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
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
 * The People screen promise: the people who can log in to your phone's file server are a short,
 * editable list. Adding someone is a name and a password; removing someone also removes what they
 * were allowed to touch, after a confirmation.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PeopleScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val alice = User("alice", "hunter2")
    private val bob = User("bob", "hunter3")

    private val shared = Share(
        id = "s1",
        name = "Photos",
        urlPath = "photos",
        localPath = "/storage/photos",
        grants = listOf(
            Grant("alice", setOf(Permission.VIEW)),
            Grant("bob", setOf(Permission.UPLOAD)),
        ),
    )

    private fun openPeople(state: AppState): FakeConfigStore {
        val store = FakeConfigStore(state)
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(store = store))
            }
        }
        compose.tap(UiTags.NavPeople)
        return store
    }

    @Test
    fun `adding a person saves the name and password and closes the dialog`() {
        val store = openPeople(AppState(users = listOf(alice), shares = listOf(shared)))

        compose.tap(UiTags.AddPerson)
        compose.onTag(UiTags.PersonDialog).assertIsDisplayed()
        compose.onTag(UiTags.PersonNameField).performTextInput("carol")
        compose.onTag(UiTags.PersonPassword).performTextInput("s3cret")
        compose.tap(UiTags.PersonSave)

        assertThat(store.state.users).contains(User("carol", "s3cret"))
        compose.onTag(UiTags.PersonDialog).assertDoesNotExist()
        compose.onTagInRow(UiTags.PersonRow, "carol", UiTags.PersonEdit).assertExists()
    }

    @Test
    fun `adding a duplicate name shows the validation message and keeps the old user`() {
        val store = openPeople(AppState(users = listOf(alice), shares = listOf(shared)))

        compose.tap(UiTags.AddPerson)
        compose.onTag(UiTags.PersonNameField).performTextInput("alice")
        compose.onTag(UiTags.PersonPassword).performTextInput("hunter3")
        compose.tap(UiTags.PersonSave)

        compose.assertTextShown("Duplicate user name")
        compose.onTag(UiTags.PersonDialog).assertIsDisplayed()
        assertThat(store.state.users).containsExactly(alice)
    }

    @Test
    fun `deleting a person removes them and their grants from shares`() {
        val store = openPeople(AppState(users = listOf(alice, bob), shares = listOf(shared)))

        compose.tap(UiTags.deletePerson("alice"))
        compose.tap(UiTags.DeletePersonConfirm)

        assertThat(store.state.users).containsExactly(bob)
        assertThat(store.state.shares.single().grants)
            .containsExactly(Grant("bob", setOf(Permission.UPLOAD)))
    }
}
