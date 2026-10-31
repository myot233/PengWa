package app.pengwa.ui

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pengwa.R

private enum class NfcState { Unsupported, Disabled, Ready }

private fun nfcState(context: Context): NfcState {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcState.Unsupported
    return if (adapter.isEnabled) NfcState.Ready else NfcState.Disabled
}

/** 名片页上的一行 NFC 提示：不支持 / 未开启（可跳设置）/ 已就绪。从系统设置回来会自动刷新。 */
@Composable
fun NfcStatus(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(nfcState(context)) }
    LifecycleResumeEffect(Unit) {
        state = nfcState(context)
        onPauseOrDispose { }
    }

    when (state) {
        NfcState.Unsupported -> StatusText(R.string.nfc_unsupported, modifier)
        NfcState.Ready -> StatusText(R.string.nfc_ready, modifier)
        NfcState.Disabled -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.nfc_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                Text(stringResource(R.string.nfc_open_settings))
            }
        }
    }
}

@Composable
private fun StatusText(resId: Int, modifier: Modifier) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
