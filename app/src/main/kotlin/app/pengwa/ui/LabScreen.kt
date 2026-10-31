package app.pengwa.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.R
import app.pengwa.ble.BlePermissions

/** 仅调试版显示：BLE 交换实验和假数据。正式版不会出现这个页面。 */
@Composable
fun LabScreen(
    modifier: Modifier = Modifier,
    bleViewModel: BleViewModel = viewModel(),
    contactsViewModel: ContactsViewModel = viewModel(),
) {
    val context = LocalContext.current

    // BLE 权限：没有权限先申请，授权后再执行原本的操作
    var pendingBleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val bleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) pendingBleAction?.invoke() else bleViewModel.log("蓝牙权限被拒绝")
        pendingBleAction = null
    }
    fun withBlePermission(action: () -> Unit) {
        if (BlePermissions.granted(context)) {
            action()
        } else {
            pendingBleAction = action
            bleLauncher.launch(BlePermissions.required())
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.lab_title), style = MaterialTheme.typography.headlineSmall)

        Text(stringResource(R.string.lab_ble), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { withBlePermission { bleViewModel.toggleAdvertising() } }) {
                Text(stringResource(if (bleViewModel.advertising) R.string.ble_stop_advertise else R.string.ble_start_advertise))
            }
            Button(onClick = { withBlePermission { bleViewModel.toggleScanning() } }) {
                Text(stringResource(if (bleViewModel.scanning) R.string.ble_stop_scan else R.string.ble_start_scan))
            }
        }
        Text(
            text = bleViewModel.logLines.take(30).joinToString("\n").ifEmpty { stringResource(R.string.ble_log_empty) },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text(stringResource(R.string.lab_demo_data), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = contactsViewModel::addDemoContacts) {
            Text(stringResource(R.string.lab_add_demo))
        }
    }
}
