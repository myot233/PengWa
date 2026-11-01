package app.pengwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pengwa.R
import app.pengwa.exchange.ExchangeMode

/**
 * NFC 页：一方出示、一方读取。角色只在本页处于前台时启用。
 */
@Composable
fun NfcPage(
    role: ExchangeMode,
    onRoleChange: (ExchangeMode) -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📲", fontSize = 72.sp)
        Text(
            text = stringResource(R.string.nfc_page_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        NfcStatus(modifier = Modifier.padding(top = 12.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).selectableGroup()) {
            listOf(
                ExchangeMode.NfcDisplay to R.string.nfc_role_display,
                ExchangeMode.NfcRead to R.string.nfc_role_read,
            ).forEach { (candidate, label) ->
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                        selected = role == candidate,
                        onClick = { onRoleChange(candidate) },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = role == candidate, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(
            text = stringResource(if (role == ExchangeMode.NfcDisplay) R.string.nfc_display_hint else R.string.nfc_read_hint),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.nfc_page_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        TextButton(onClick = onShowQr, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.nfc_show_qr))
        }
    }
}
