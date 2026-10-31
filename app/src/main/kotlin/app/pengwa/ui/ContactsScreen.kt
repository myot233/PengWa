package app.pengwa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.R
import app.pengwa.contacts.Contact
import java.text.DateFormat
import java.util.Date

@Composable
fun ContactsScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = viewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        if (contacts.isEmpty()) {
            Text(
                text = stringResource(R.string.contacts_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else {
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts, key = { it.id }) { contact ->
                    ContactRow(contact, lastMet = dateFormat.format(Date(contact.lastMetAt)))
                    HorizontalDivider()
                }
                // 给右下角的拍照按钮留出空间，最后一行不会被挡住
                item { Box(Modifier.padding(bottom = 88.dp)) }
            }
        }

        CameraButton(
            onMessage = onMessage,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun ContactRow(contact: Contact, lastMet: String) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.contact_met_summary,
                    contact.metCount,
                    contact.metCount,
                    lastMet,
                ),
            )
        },
        trailingContent = {
            Text(
                text = contact.id.toString().take(8),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
