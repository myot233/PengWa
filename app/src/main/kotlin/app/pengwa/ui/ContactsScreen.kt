package app.pengwa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.R
import app.pengwa.contacts.Contact
import app.pengwa.ui.components.NameStamp
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.PageGutter
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun ContactsScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExchange: () -> Unit = {},
    viewModel: ContactsViewModel = viewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    ContactsScreenContent(contacts = contacts, modifier = modifier, onExchange = onExchange) { buttonModifier ->
        CameraButton(onMessage = onMessage, modifier = buttonModifier)
    }
}

@Composable
internal fun ContactsScreenContent(
    contacts: List<Contact>,
    modifier: Modifier = Modifier,
    onExchange: () -> Unit = {},
    now: Long = System.currentTimeMillis(),
    cameraButton: @Composable (Modifier) -> Unit,
) {
    var selected by remember { mutableStateOf<Contact?>(null) }
    var fabHeight by remember { mutableIntStateOf(56) }
    val fabClearance = with(LocalDensity.current) { fabHeight.toDp() } + 40.dp
    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = PageGutter)) {
            Text(stringResource(R.string.tab_contacts), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
            Text(stringResource(R.string.contacts_count, contacts.size), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
            if (contacts.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = fabClearance),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        PengWaIcon(PengWaIcons.Book, modifier = Modifier.padding(24.dp).size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(stringResource(R.string.contacts_empty_title), style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
                    Text(stringResource(R.string.contacts_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = onExchange, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.contacts_exchange))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = fabClearance)) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactRow(contact, now, onClick = { selected = contact })
                    }
                }
            }
        }
        cameraButton(Modifier.align(Alignment.BottomEnd).padding(20.dp).onSizeChanged { fabHeight = it.height })
    }
    selected?.let { contact -> ContactDetails(contact) { selected = null } }
}

@Composable
private fun ContactRow(contact: Contact, now: Long, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NameStamp(contact.displayName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.contact_times, contact.metCount), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(compactContactDate(contact.lastMetAt, now), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PengWaIcon(PengWaIcons.Next, modifier = Modifier.align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun ContactDetails(contact: Contact, onDismiss: () -> Unit) {
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PengWaIcon(PengWaIcons.Book, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(contact.displayName) },
        text = {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.contact_times, contact.metCount), color = MaterialTheme.colorScheme.primary)
                    DetailField(stringResource(R.string.contact_first), format.format(Date(contact.firstMetAt)))
                    DetailField(stringResource(R.string.contact_last), format.format(Date(contact.lastMetAt)))
                    DetailField(stringResource(R.string.contact_id), contact.id.toString())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun compactContactDate(timestamp: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (date.toLocalDate()) {
        today -> "今天 " + date.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "M 月 d 日" else "yyyy 年 M 月 d 日", Locale.getDefault()))
    }
}
