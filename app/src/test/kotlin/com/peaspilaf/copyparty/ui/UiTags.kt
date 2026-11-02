package com.peaspilaf.copyparty.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
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
 * unmerged tree, so a tag inside a merged row still resolves.
 *
 * The shell is a bottom navigation bar with four tabs. Each tab shows one screen, and each screen
 * root carries its own tag ([ScreenHome] and friends) so navigation can be asserted without
 * depending on any particular copy.
 *
 * Screens scroll vertically and keep every control composed (no lazy lists for forms), because the
 * tests tap controls by tag without guessing where they ended up.
 *
 * Repeating rows (shares, people) all carry the same row tag. To reach a control inside one
 * particular row, use [onTagInRow] / [tapInRow] with some text from that row: a tag alone cannot
 * say *which* share's Edit button to press.
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

    // -- Home: one glance says whether this phone is serving files ----------------------------

    /** Starts the server. Disabled when there is nothing shareable, or a setting cannot work. */
    const val HomeStart = "home-start"

    /** Stops the running server. */
    const val HomeStop = "home-stop"

    /** The primary reachable address, on the `Text` that shows it. */
    const val HomeAddress = "home-address"

    /** Copies the address. Confirms with a snackbar - copying is never silent. */
    const val HomeCopy = "home-copy"

    /** Opens the full-screen QR code for the running address. */
    const val HomeQr = "home-qr"

    /** Opens the in-app file browser for the running server. */
    const val HomeBrowse = "home-browse"

    /** Opens the server log. */
    const val HomeServerLog = "home-server-log"

    /** Opens the expert tools. Only shown when expert mode is on. */
    const val ExpertEntry = "expert-entry"

    /** "Add files": copies documents picked from the system picker into the app's share folder. */
    const val ImportFiles = "import-files"

    /** The two-step first-run card: 1 Choose a folder, 2 Start serving. Shown while nothing is shared. */
    const val FirstRunChecklist = "first-run-checklist"

    /** The checklist action that opens the folder picker. */
    const val FirstRunChooseFolder = "first-run-choose-folder"

    /**
     * "Restart to apply": the one action on the banner that says saved changes are not live yet.
     * Same tag on Home and on Shares, because the banner follows the user.
     */
    const val HomeRestartToApply = "home-restart-to-apply"

    /** The one-line status text ("Server stopped", "Starting...", ...). */
    const val ServerStatus = "server-status"

    /** Banner that lists the validation ERRORs; only present when there are any. */
    const val ValidationBanner = "validation-banner"

    // -- Storage access -----------------------------------------------------------------------

    /** "Allow access to all files": fires `StorageAccess.allFilesAccessIntent()`. */
    const val StorageGrant = "storage-grant"

    // -- Folder picker ------------------------------------------------------------------------

    /** The folder picker container; present only while it is open. */
    const val FolderPicker = "folder-picker"

    /** Root chooser entry for the app's own folder ("App folder"). */
    const val PickerRootApp = "picker-root-app"

    /** Root chooser entry for internal storage ("This phone"). Only when All files access exists. */
    const val PickerRootPhone = "picker-root-phone"

    /** The breadcrumb of the folder the picker is showing, e.g. "App folder / Photos". */
    const val PickerBreadcrumb = "picker-breadcrumb"

    /** Goes one folder up; at a root it returns to the root chooser. */
    const val PickerUp = "picker-up"

    /** "Use this folder": selects the folder the picker is showing. */
    const val PickerSelect = "picker-select"

    /** "This folder cannot be opened" - distinct from an empty folder. */
    const val PickerUnreadable = "picker-unreadable"

    /** "No subfolders here" - an empty folder is not an error. */
    const val PickerEmpty = "picker-empty"

    /** The button for one subfolder inside the picker, e.g. `picker-folder-Photos`. */
    fun pickerFolder(name: String): String = "picker-folder-$name"

    // -- Shares: the folder list --------------------------------------------------------------

    /** Opens the folder picker for a new share. */
    const val AddShare = "add-share"

    /** The "add the first folder" action inside the Shares empty state. */
    const val EmptyAddShare = "empty-add-share"

    /** One per share, on the row container. */
    const val ShareRow = "share-row"

    /** The share name, on the `Text` that shows it. */
    const val ShareName = "share-name"

    /** The share's link: the full URL while the server runs, the mount path while it is stopped. */
    const val ShareUrl = "share-url"

    /** Who may open the share ("alice, bob", "Everyone"), on the `Text` that shows it. */
    const val ShareAccess = "share-access"

    /** The per-share enable `Switch`. Turning it off keeps the share but stops serving it. */
    const val ShareEnabled = "share-enabled"

    /** Opens the share editor for the row's share. */
    const val ShareEdit = "share-edit"

    /** Copies the share's full URL. Confirms with a snackbar. */
    const val ShareCopy = "share-copy"

    /** Hands the share's full URL to another app (ACTION_SEND). Confirms with a snackbar. */
    const val ShareSend = "share-send"

    /** Deletes the share from its row; opens the confirmation. */
    const val ShareDelete = "share-delete"

    /** The message of the row-delete confirmation; contains the folder name and "kept". */
    const val ShareDeleteDialog = "share-delete-dialog"

    /** Confirms the row delete. */
    const val ShareDeleteConfirm = "share-delete-confirm"

    /** Cancels the row delete and keeps the share. */
    const val ShareDeleteCancel = "share-delete-cancel"

    // -- Share editor -------------------------------------------------------------------------

    /** The editor container; present only while the editor is open. */
    const val ShareEditor = "share-editor"

    /** Leaves the editor without saving. */
    const val ShareEditorBack = "share-editor-back"

    /** Saves the edited share through `AppViewModel.saveShare`; a share nobody can open is refused. */
    const val ShareEditorSave = "share-editor-save"

    /** The share's link path (`urlPath`) input field. */
    const val ShareLinkField = "share-link-field"

    /** The folder the share is rooted in. */
    const val EditorFolder = "editor-folder"

    /** Opens the folder picker to re-root the share; the existing grants are kept. */
    const val EditorChangeFolder = "editor-change-folder"

    /** The inline "nobody can open this share" message that blocks saving. */
    const val EditorNoAccess = "editor-no-access"

    /** Deletes the share being edited; opens the confirmation. */
    const val EditorDelete = "editor-delete"

    /** The message of the editor-delete confirmation; contains the folder name and "kept". */
    const val EditorDeleteDialog = "editor-delete-dialog"

    /** Confirms the editor delete and returns to the list. */
    const val EditorDeleteConfirm = "editor-delete-confirm"

    /** Cancels the editor delete and keeps the share. */
    const val EditorDeleteCancel = "editor-delete-cancel"

    /** Quick preset: anyone on the network can view and download. */
    const val PresetAnyoneView = "preset-anyone-view"

    /** Quick preset: only the people ticked below can open the share. */
    const val PresetPeople = "preset-people"

    /** Quick preset: anyone on the network can upload (a dropbox). */
    const val PresetDropbox = "preset-dropbox"

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

    /** Opens the add-person form. */
    const val AddPerson = "add-person"

    /** The "add the first person" action inside the People empty state. */
    const val EmptyAddPerson = "empty-add-person"

    /** The add/edit person form container; present only while it is open. */
    const val PersonDialog = "person-dialog"

    /** The person's name field. */
    const val PersonNameField = "person-name-field"

    /** The person's password field. Masked until [PersonPasswordReveal] is tapped. */
    const val PersonPassword = "person-password"

    /** Fills [PersonPassword] with a fresh password from `AppViewModel.randomPassword()`. */
    const val PersonGenerate = "person-generate"

    /** Shows/hides the password while it is being typed. */
    const val PersonPasswordReveal = "person-password-reveal"

    /** Saves the person through `addUser`/`updateUser`; a duplicate name keeps the form open. */
    const val PersonSave = "person-save"

    /** One per person, on the row container. */
    const val PersonRow = "person-row"

    /** Opens the form to rename the person or reset their password. */
    const val PersonEdit = "person-edit"

    /** Deletes the person through `deleteUser` after a confirmation. */
    fun deletePerson(name: String): String = "delete-person-$name"

    /** The message of the delete confirmation; names the shares that lose access. */
    const val DeletePersonDialog = "delete-person-dialog"

    /** Confirms deleting the person. */
    const val DeletePersonConfirm = "delete-person-confirm"

    /** Cancels deleting the person. */
    const val DeletePersonCancel = "delete-person-cancel"

    // -- Connect: in-app browser, QR code and copy --------------------------------------------

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

    /** "Expert mode" `Switch`. Saves immediately and, when switched on, opens the expert tools. */
    const val ExpertToggle = "expert-toggle"

    /** The "Expert tools" entry inside Settings; present only while expert mode is on. */
    const val SettingsExpert = "settings-expert"

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
 * Finds [tag] inside the row that has [rowTag] and shows [rowText]. Repeating rows share one tag,
 * so the row's own text is what identifies it.
 */
fun SemanticsNodeInteractionsProvider.onTagInRow(
    rowTag: String,
    rowText: String,
    tag: String,
): SemanticsNodeInteraction = onNode(
    hasTestTag(tag) and hasAnyAncestor(
        hasTestTag(rowTag) and hasAnyDescendant(hasText(rowText, substring = true)),
    ),
    useUnmergedTree = true,
)

/** Finds [dialogTag] when its message contains [text], so the dialog - not the screen behind it - is asserted. */
fun SemanticsNodeInteractionsProvider.onDialogContaining(
    dialogTag: String,
    text: String,
): SemanticsNodeInteraction = onNode(
    hasTestTag(dialogTag) and hasAnyDescendant(hasText(text, substring = true)),
    useUnmergedTree = true,
)

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

/** Clicks [tag] inside the row identified by [rowText]. */
fun SemanticsNodeInteractionsProvider.tapInRow(rowTag: String, rowText: String, tag: String) {
    val node = onTagInRow(rowTag, rowText, tag)
    if (!node.isDisplayed()) {
        node.performScrollTo()
    }
    node.performClick()
}

/** Asserts that some node on screen shows [text] (substring, so surrounding copy does not matter). */
fun SemanticsNodeInteractionsProvider.assertTextShown(text: String) {
    onAllNodes(hasText(text, substring = true)).onFirst().assertExists()
}

/**
 * Waits for [text] to appear anywhere, ignoring case. Confirmations are delivered as snackbars and
 * that takes a frame, so the assertion has to wait rather than race the animation.
 */
fun ComposeTestRule.awaitText(text: String, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodes(hasText(text, substring = true, ignoreCase = true))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
}
