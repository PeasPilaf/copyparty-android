package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.core.Validator
import com.peaspilaf.copyparty.server.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The people who can sign in to this phone's file server: a short, editable list.
 *
 * A person can be renamed or given a new password without losing what they could already open, the
 * password is generated for you and masked until asked for, and removing someone says which folders
 * lose access - then offers one tap to undo it.
 */
@Composable
internal fun PeopleScreen(
    viewModel: AppViewModel,
    state: AppState,
    showRestartToApply: Boolean = false,
    onRestartToApply: () -> Unit = {},
    restartFailure: String? = null,
) {
    val running = viewModel.serverStatus.collectAsState().value is ServerStatus.Running
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var formOpen by remember { mutableStateOf(false) }
    var originalName by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var passwordDraft by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var formErrors by remember { mutableStateOf(emptyList<String>()) }
    var deleting by remember { mutableStateOf<User?>(null) }

    fun openAdd() {
        formOpen = true
        originalName = null
        nameDraft = ""
        passwordDraft = ""
        revealed = false
        formErrors = emptyList()
    }

    fun openEdit(user: User) {
        formOpen = true
        originalName = user.name
        nameDraft = user.name
        passwordDraft = user.password
        revealed = false
        formErrors = emptyList()
    }

    fun save() {
        val edited = User(nameDraft.trim(), passwordDraft)
        val index = if (originalName == null) {
            state.users.size
        } else {
            state.users.indexOfFirst { it.name == originalName }
        }
        val users = if (originalName == null) {
            state.users + edited
        } else {
            state.users.toMutableList().also { if (index >= 0) it[index] = edited }
        }
        // The whole user list is validated, not just the edited index: renaming alice to bob is
        // reported on the *later* bob entry, and that duplicate has to keep the form open too.
        val problems = Validator.validate(state.copy(users = users))
            .filter { it.severity == Severity.ERROR && it.path.startsWith("users[") }
        if (problems.isNotEmpty()) {
            formErrors = problems.map { it.message }
            return
        }
        if (originalName == null) {
            viewModel.addUser(edited.name, edited.password)
        } else {
            viewModel.updateUser(originalName.orEmpty(), edited.name, edited.password)
        }
        formOpen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag(Tags.ScreenPeople),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showRestartToApply) {
                RestartToApplyBanner(onRestartToApply = onRestartToApply, failure = restartFailure)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "People", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Each person signs in with a name and password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { openAdd() },
                    modifier = Modifier.testTag(Tags.AddPerson),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (formOpen) {
                PersonForm(
                    title = if (originalName == null) "Add a person" else "Edit ${originalName}",
                    name = nameDraft,
                    onNameChange = { nameDraft = it },
                    password = passwordDraft,
                    onPasswordChange = { passwordDraft = it },
                    revealed = revealed,
                    onRevealToggle = { revealed = !revealed },
                    onGenerate = { passwordDraft = viewModel.randomPassword() },
                    errors = formErrors,
                    onSave = { save() },
                    onCancel = { formOpen = false },
                )
            }

            if (state.users.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No people yet. Adding someone is a name and a password.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = { openAdd() },
                            modifier = Modifier.testTag(Tags.EmptyAddPerson),
                        ) {
                            Text("Add the first person")
                        }
                    }
                }
            }

            for (user in state.users) {
                PersonRow(
                    user = user,
                    onEdit = { openEdit(user) },
                    onDelete = { deleting = user },
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val removed = deleting
    if (removed != null) {
        val losing = state.shares
            .filter { share -> share.grants.any { it.principal == removed.name && it.permissions.isNotEmpty() } }
            .map { it.name }
        val effect = if (running) {
            " The running server keeps the old account until you restart it."
        } else {
            ""
        }
        val message = if (losing.isEmpty()) {
            "Removing ${removed.name} deletes the account. No shared folder gives them access.$effect"
        } else {
            "Removing ${removed.name} deletes the account. They will lose access to " +
                "${losing.joinToString(", ")}. The folders and their files are kept.$effect"
        }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${removed.name}?") },
            text = {
                Column(modifier = Modifier.testTag(Tags.DeletePersonDialog)) {
                    Text(text = message)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        deleteAndOfferUndo(viewModel, state, removed, scope, snackbar)
                    },
                    modifier = Modifier.testTag(Tags.DeletePersonConfirm),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    modifier = Modifier.testTag(Tags.DeletePersonCancel),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Deletes [user] and keeps what was taken away, so the snackbar's Undo can put the account *and* its
 * grants back exactly as they were. The restore re-reads the live state: the user may have changed
 * an unrelated share in the meantime, and that change must survive the undo.
 */
private fun deleteAndOfferUndo(
    viewModel: AppViewModel,
    state: AppState,
    user: User,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
) {
    val removedGrants = state.shares.mapNotNull { share ->
        val grants = share.grants.filter { it.principal == user.name }
        if (grants.isEmpty()) null else share.id to grants
    }
    viewModel.deleteUser(user.name)
    scope.launch {
        val result = snackbar.showSnackbar(
            message = "${user.name} removed",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.addUser(user.name, user.password)
            for ((shareId, grants) in removedGrants) {
                val share = viewModel.state.value.shares.firstOrNull { it.id == shareId } ?: continue
                val missing = grants.filter { grant -> share.grants.none { it.principal == grant.principal } }
                if (missing.isNotEmpty()) {
                    viewModel.saveShare(share.copy(grants = share.grants + missing))
                }
            }
        }
    }
}

@Composable
private fun PersonForm(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    revealed: Boolean,
    onRevealToggle: () -> Unit,
    onGenerate: () -> Unit,
    errors: List<String>,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(Tags.PersonDialog)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(Tags.PersonNameField),
            )
            // The field reports its own `Password` semantics while the visual transformation is a
            // password one, but a semantics node keeps properties it was built with: revealing the
            // password flips the transformation and the old node would still claim to be masked. A
            // keyed field is a new node, so the promise "masked until revealed" stays true to
            // accessibility as well as to the eye. Its editable text stays the real password (the
            // masking is only the display), which is what lets the form read back what was generated.
            key(revealed) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (revealed) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = onRevealToggle,
                            modifier = Modifier.testTag(Tags.PersonPasswordReveal),
                        ) {
                            Text(if (revealed) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(Tags.PersonPassword)
                        .semantics { editableText = AnnotatedString(password) },
                )
            }
            OutlinedButton(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth().testTag(Tags.PersonGenerate),
            ) {
                Text("Generate a password")
            }
            for (message in errors) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave, modifier = Modifier.testTag(Tags.PersonSave)) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun PersonRow(user: User, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(Tags.PersonRow)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEdit, modifier = Modifier.testTag(Tags.PersonEdit)) {
                Text("Edit")
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(Tags.deletePerson(user.name)),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${user.name}")
            }
        }
    }
}
