package app.pengwa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pengwa.R

@Composable
internal fun ExchangeRequestDialog(name: String, shortId: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { PengWaIcon(PengWaIcons.Identity, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.request_title, name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.request_source), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.request_body, shortId))
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.request_accept_in_app)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.request_decline)) } },
    )
}
