package app.pengwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.R
import app.pengwa.pengWa

/**
 * 仅调试版显示：蓝牙交换的运行状态与日志、假数据。正式版不会出现这个页面。
 * 蓝牙的开关在名片页，这里只是只读的观察窗口，方便和第二台手机联调时看发生了什么。
 */
@Composable
fun LabScreen(
    modifier: Modifier = Modifier,
    contactsViewModel: ContactsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val controller = remember { context.pengWa.bleExchange }
    val running by controller.running.collectAsStateWithLifecycle()
    val bluetoothOn by controller.bluetoothOn.collectAsStateWithLifecycle()
    val log by controller.logLines.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.lab_title), style = MaterialTheme.typography.headlineSmall)

        Text(stringResource(R.string.lab_ble), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(
                R.string.lab_ble_status,
                stringResource(if (running) R.string.lab_running else R.string.lab_stopped),
                stringResource(if (bluetoothOn) R.string.lab_on else R.string.lab_off),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = log.take(40).joinToString("\n").ifEmpty { stringResource(R.string.ble_log_empty) },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text(stringResource(R.string.lab_demo_data), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = contactsViewModel::addDemoContacts) {
            Text(stringResource(R.string.lab_add_demo))
        }
        // 点完可以按 Home 退到后台，5 秒后会收到一条带"同意 / 拒绝"按钮的通知，验证后台提醒
        OutlinedButton(onClick = { controller.debugSimulateIncomingRequest("模拟小熊", 5_000) }) {
            Text(stringResource(R.string.lab_simulate_request))
        }
    }
}
