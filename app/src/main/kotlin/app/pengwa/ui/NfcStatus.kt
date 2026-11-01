package app.pengwa.ui

import android.content.Context
import android.nfc.NfcAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.LocalStatusColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pengwa.R

internal enum class NfcState { Unsupported, Disabled, Ready }

private fun nfcState(context: Context): NfcState {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcState.Unsupported
    return if (adapter.isEnabled) NfcState.Ready else NfcState.Disabled
}

@Composable
internal fun rememberNfcState(): NfcState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(nfcState(context)) }
    LifecycleResumeEffect(Unit) {
        state = nfcState(context)
        onPauseOrDispose { }
    }

    return state
}

@Composable
internal fun NfcStatus(
    state: NfcState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalStatusColors.current
    val color = when (state) {
        NfcState.Ready -> statusColors.success
        NfcState.Disabled -> statusColors.warning
        NfcState.Unsupported -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.widthIn(max = 340.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PengWaIcon(PengWaIcons.Tap, tint = color)
        Text(
            text = stringResource(when (state) {
                NfcState.Ready -> R.string.nfc_ready
                NfcState.Disabled -> R.string.nfc_disabled
                NfcState.Unsupported -> R.string.nfc_unsupported
            }),
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium, color = color,
        )
        if (state == NfcState.Disabled) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.nfc_open_settings)) }
        }
    }
}
