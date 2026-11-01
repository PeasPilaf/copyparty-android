package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.core.Validator

/**
 * The people who can sign in to this phone's file server: a short, editable list. Removing someone
 * also removes what they were allowed to touch - the ViewModel rewrites every share's grants.
 */
@Composable
internal fun PeopleScreen(viewModel: AppViewModel, state: AppState) {
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(Tags.ScreenPeople),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                onClick = { adding = true },
                modifier = Modifier.testTag(Tags.AddPerson),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (adding) {
            AddPersonForm(
                state = state,
                onAdd = { name, password ->
                    viewModel.addUser(name, password)
                    adding = false
                },
                onCancel = { adding = false },
            )
        }

        if (state.users.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "No people yet. Adding someone is a name and a password.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        for (user in state.users) {
            PersonRow(
                user = user,
                onDelete = { viewModel.deleteUser(user.name) },
            )
        }
    }
}

@Composable
private fun AddPersonForm(
    state: AppState,
    onAdd: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    fun submit() {
        val trimmed = name.trim()
        val candidate = state.copy(users = state.users + User(trimmed, password))
        val index = state.users.size
        val found = Validator.validate(candidate).filter { issue ->
            issue.severity == Severity.ERROR && issue.path.startsWith("users[$index]")
        }
        if (found.isEmpty()) {
            onAdd(trimmed, password)
        } else {
            errors = found.map { it.message }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().testTag(Tags.PersonDialog)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Add a person", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(Tags.PersonNameField),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(Tags.PersonPasswordField),
            )
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
                Button(onClick = { submit() }, modifier = Modifier.testTag(Tags.PersonSave)) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun PersonRow(user: User, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(Tags.personRow(user.name))) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(Tags.deletePerson(user.name)),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${user.name}")
            }
        }
    }
}
