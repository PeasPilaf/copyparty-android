package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.peaspilaf.copyparty.core.Permission

/**
 * The screen contract, written as testTags.
 *
 * Every constant below is a promise to the user and to the implementation: the composable named in
 * the comment sets exactly this tag on the node that carries the value - the `Text` for text, the
 * `Switch` for switches, the `TextField` for input fields. Tests always look nodes up in the
 * unmerged tree, so a tag inside a clickable row still resolves.
 *
 * The shell is a bottom navigation bar with four tabs. Each tab shows one screen, and each screen
 * root carries its own tag ([ScreenHome] and friends) so navigation can be asserted without
 * depending on any particular copy.
 *
 * Screens scroll vertically and keep every control composed (no lazy lists for forms), because the
 * tests tap controls by tag without guessing where they ended up.
 */
object UiTags {

    // -- App shell ----------------------------------------------------------------------------

    const val NavHome = "nav-home"
    const val NavShares = "nav-shares"
    const val NavPeople = "nav-people"
    const val NavSettings = "nav-settings"

    const val ScreenHome = "screen-home"
    const val ScreenShares = "screen-shares"
    const val ScreenPeople = "screen-people"
    const val ScreenSettings = "screen-settings"

    // -- Home: start/stop the server and explain why it cannot start --------------------------

    /** Start button. Disabled (not hidden) while [com.peaspilaf.copyparty.core.Validator] reports errors. */
    const val StartServer = "start-server"

    /** Stop button, shown while the server runs. */
    const val StopServer = "stop-server"

    /** The one-line status text ("Server stopped", "Starting...", ...). */
    const val ServerStatus = "server-status"

    /** One `Text` per reachable URL, shown while the server runs. */
    const val ServerUrl = "server-url"

    /** Banner that lists the validation ERRORs; only present when there are any. */
    const val ValidationBanner = "validation-banner"

    // -- Shares: the folder list --------------------------------------------------------------

    /** Opens the folder picker for a new share. */
    const val AddShare = "add-share"

    /** One per share, on the row container. */
    const val ShareRow = "share-row"

    /** The share name, on the `Text` that shows it. */
    const val ShareName = "share-name"

    /** The link path (`/photos`), on the `Text` that shows it. */
    const val SharePath = "share-path"

    /** Who may open the share ("alice, bob", "Everyone"), on the `Text` that shows it. */
    const val ShareAccess = "share-access"

    /** The per-share enable `Switch`. Turning it off keeps the share but stops serving it. */
    const val ShareEnabled = "share-enabled"

    /** Opens the share editor for the row's share. */
    const val ShareEdit = "share-edit"

    /** The folder picker container; present only while it is open. */
    const val FolderPicker = "folder-picker"

    /** "Use this folder": selects the folder the picker is showing (it starts in the app share folder). */
    const val FolderSelect = "folder-select"

    // -- Share editor -------------------------------------------------------------------------

    /** The editor container; present only while the editor is open. */
    const val ShareEditor = "share-editor"

    /** Leaves the editor without saving. */
    const val ShareEditorBack = "share-editor-back"

    /** Saves the edited share through `AppViewModel.saveShare`. */
    const val ShareEditorSave = "share-editor-save"

    /** The share's link path (`urlPath`) input field. */
    const val ShareLinkField = "share-link-field"

    /** "Anyone on the network" `Switch`: on adds a `*` grant, off removes it. */
    const val AnyoneOnNetwork = "anyone-on-network"

    /** Banner that lists the validator ERRORs for the share being edited; only present when there are any. */
    const val ShareIssues = "share-issues"

    /**
     * One permission `Checkbox`/`Switch` for one named user, e.g. `permission-alice-VIEW`.
     * The editor renders one control per configured user and per [Permission].
     */
    fun permission(principal: String, permission: Permission): String =
        "permission-$principal-${permission.name}"

    // -- People -------------------------------------------------------------------------------

    /** Opens the add-person dialog. */
    const val AddPerson = "add-person"

    /** The add-person dialog container; present only while the dialog is open. */
    const val PersonDialog = "person-dialog"

    /** The new person's name field. */
    const val PersonNameField = "person-name-field"

    /** The new person's password field. */
    const val PersonPasswordField = "person-password-field"

    /** Saves the person through `AppViewModel.addUser`; a duplicate name keeps the dialog open. */
    const val PersonSave = "person-save"

    /** One per person, on the row container. */
    fun personRow(name: String): String = "person-row-$name"

    /** Deletes the person through `AppViewModel.deleteUser`, which also strips their grants. */
    fun deletePerson(name: String): String = "delete-person-$name"

    // -- Connect: in-app browser and QR code ---------------------------------------------------

    /** Opens the real copyparty web UI in the in-app browser. */
    const val OpenBrowser = "open-browser"

    /** Opens the full-screen QR code for the running address. */
    const val ShowQr = "show-qr"

    /** Copies the running address to the clipboard. */
    const val CopyUrl = "copy-url"

    const val ScreenQr = "screen-qr"
    const val QrBack = "qr-back"
    const val QrImage = "qr-image"
    const val QrUrl = "qr-url"
    const val QrCopy = "qr-copy"

    const val BrowserChrome = "browser-chrome"
    const val BrowserWebView = "browser-webview"
    const val BrowserBack = "browser-back"
    const val BrowserUrl = "browser-url"
    const val BrowserReload = "browser-reload"
    const val BrowserExternal = "browser-external"

    // -- Settings -----------------------------------------------------------------------------

    /** The port input field. */
    const val SettingsPort = "settings-port"

    /** The "accessible from the local network" `Switch` (`0.0.0.0` vs `127.0.0.1`). */
    const val SettingsLanAccess = "settings-lan-access"

    /** The "start on boot" `Switch`. */
    const val SettingsStartOnBoot = "settings-start-on-boot"

    /** The server name input field. */
    const val SettingsServerName = "settings-server-name"

    /** Saves the form through `AppViewModel.updateSettings`; invalid values are not saved. */
    const val SettingsSave = "settings-save"

    /** Banner that lists what is wrong with the form; only present when a save was refused. */
    const val SettingsErrors = "settings-errors"

    const val ThemeSystem = "theme-SYSTEM"
    const val ThemeLight = "theme-LIGHT"
    const val ThemeDark = "theme-DARK"
}

/** Finds a tagged node in the unmerged tree, so tags inside merged rows still resolve. */
fun SemanticsNodeInteractionsProvider.onTag(tag: String): SemanticsNodeInteraction =
    onNodeWithTag(tag, useUnmergedTree = true)

/** Finds every node with [tag] in the unmerged tree. */
fun SemanticsNodeInteractionsProvider.onAllTags(tag: String): SemanticsNodeInteractionCollection =
    onAllNodesWithTag(tag, useUnmergedTree = true)

/**
 * Clicks the tagged control, scrolling its screen first if the control sits below the fold. If the
 * control exists but its screen does not scroll, this fails - which is the signal that the screen
 * must be made scrollable.
 */
fun SemanticsNodeInteractionsProvider.tap(tag: String) {
    val node = onTag(tag)
    if (!node.isDisplayed()) {
        node.performScrollTo()
    }
    node.performClick()
}

/** Asserts that some node on screen shows [text] (substring, so surrounding copy does not matter). */
fun SemanticsNodeInteractionsProvider.assertTextShown(text: String) {
    onAllNodes(hasText(text, substring = true)).onFirst().assertExists()
}
