package app.pengwa.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pengwa.R
import app.pengwa.ble.BlePermissions
import app.pengwa.ble.NearbyPeer
import app.pengwa.ble.PeerState
import app.pengwa.pengWa

/**
 * 蓝牙页：停在这一页时，近场的碰碰娃互相可以发现，列成一个列表，**点谁才和谁交换**，不会自动交换。
 * 没权限或系统蓝牙没开时，这一页会引导用户开启。
 */
@Composable
fun BluetoothPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { context.pengWa.bleExchange }
    val nearby by controller.nearby.collectAsStateWithLifecycle()
    val bluetoothOn by controller.bluetoothOn.collectAsStateWithLifecycle()
    var permitted by remember { mutableStateOf(BlePermissions.granted(context)) }
    var denied by remember { mutableStateOf(false) }

    // 从系统设置回来后重新检查权限和蓝牙状态
    LifecycleResumeEffect(Unit) {
        permitted = BlePermissions.granted(context)
        if (permitted) denied = false
        controller.refresh()
        onPauseOrDispose { }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.refresh()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permitted = result.values.all { it }
        denied = !permitted
        controller.refresh()
        if (permitted && !controller.bluetoothOn.value) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    BluetoothPageContent(
        permitted = permitted,
        denied = denied,
        bluetoothOn = bluetoothOn,
        nearby = nearby,
        onPermission = {
            if (denied) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            } else {
                permissionLauncher.launch(BlePermissions.required())
            }
        },
        onEnableBluetooth = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        onExchange = { controller.exchangeWith(it) },
        modifier = modifier,
    )
}

@Composable
internal fun BluetoothPageContent(
    permitted: Boolean,
    denied: Boolean,
    bluetoothOn: Boolean,
    nearby: List<NearbyPeer>,
    onPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onExchange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            !permitted -> Prompt(
                message = stringResource(if (denied) R.string.bt_permission_needed else R.string.bt_permission_intro),
                actionLabel = stringResource(if (denied) R.string.bt_open_app_settings else R.string.bt_grant),
                onAction = onPermission,
            )
            !bluetoothOn -> Prompt(
                message = stringResource(R.string.bt_off),
                actionLabel = stringResource(R.string.bt_turn_on),
                onAction = onEnableBluetooth,
            )
            nearby.isEmpty() -> Prompt(message = stringResource(R.string.bt_searching), actionLabel = null) {}
            else -> {
                Text(
                    text = stringResource(R.string.bt_nearby_title, nearby.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(nearby, key = { it.address }) { peer ->
                        PeerRow(peer, onExchange = { onExchange(peer.address) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Prompt(message: String, actionLabel: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (actionLabel == null) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (actionLabel == null) 16.dp else 0.dp),
        )
        if (actionLabel != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PeerRow(peer: NearbyPeer, onExchange: () -> Unit) {
    val busy = peer.state == PeerState.Exchanging || peer.state == PeerState.Waiting
    ListItem(
        modifier = Modifier.clickable(enabled = !busy, onClick = onExchange),
        headlineContent = { Text(peer.name ?: stringResource(R.string.bt_peer_unknown)) },
        supportingContent = {
            Text(
                text = when {
                    peer.state == PeerState.Failed -> peer.message ?: stringResource(R.string.bt_failed)
                    peer.state == PeerState.Exchanging -> stringResource(R.string.bt_requesting)
                    peer.state == PeerState.Waiting -> stringResource(R.string.bt_waiting)
                    peer.name == null -> stringResource(R.string.bt_reading_name)
                    else -> stringResource(signalLabel(peer.rssi))
                },
                color = if (peer.state == PeerState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            when (peer.state) {
                PeerState.Exchanging, PeerState.Waiting ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                PeerState.Done -> Text(stringResource(R.string.bt_done), color = MaterialTheme.colorScheme.primary)
                PeerState.Failed -> TextButton(onClick = onExchange) { Text(stringResource(R.string.bt_retry)) }
                else -> OutlinedButton(onClick = onExchange) { Text(stringResource(R.string.bt_request)) }
            }
        },
    )
}

private fun signalLabel(rssi: Int): Int = when {
    rssi >= -60 -> R.string.bt_signal_strong
    rssi >= -75 -> R.string.bt_signal_medium
    else -> R.string.bt_signal_weak
}
