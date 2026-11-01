package app.pengwa.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import app.pengwa.ui.components.NameStamp
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.PageGutter
import app.pengwa.ui.theme.LocalStatusColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

/** Bottom navigation owns the foreground Bluetooth session; permission and scanning remain in BluetoothPage. */
@Composable
fun NearbyScreen(modifier: Modifier = Modifier) {
    ExchangeSession(app.pengwa.exchange.ExchangeMode.Bluetooth)
    BluetoothPage(modifier)
}

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
    Column(modifier = modifier.fillMaxSize().padding(horizontal = PageGutter)) {
        Text(stringResource(R.string.tab_nearby), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
        Text(stringResource(R.string.bt_consent_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        when {
            !permitted -> Prompt(
                message = stringResource(if (denied) R.string.bt_permission_needed else R.string.bt_permission_intro),
                actionLabel = stringResource(if (denied) R.string.bt_open_app_settings else R.string.bt_grant),
                onAction = onPermission,
            )
            !bluetoothOn -> Prompt(
                message = stringResource(R.string.bt_off), actionLabel = stringResource(R.string.bt_turn_on), onAction = onEnableBluetooth,
            )
            nearby.isEmpty() -> Prompt(message = stringResource(R.string.bt_search_help), actionLabel = null) {}
            else -> {
                Text(stringResource(R.string.bt_search_count, nearby.size), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(nearby, key = { it.address }) { peer -> PeerRow(peer) { onExchange(peer.address) } }
                }
            }
        }
    }
}

@Composable
private fun Prompt(message: String, actionLabel: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            PengWaIcon(PengWaIcons.Bluetooth, modifier = Modifier.padding(20.dp).size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (actionLabel == null) {
            Text(stringResource(R.string.bt_search_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).size(24.dp), strokeWidth = 2.dp)
        }
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        if (actionLabel != null) Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp)) { Text(actionLabel) }
    }
}

@Composable
private fun PeerRow(peer: NearbyPeer, onExchange: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NameStamp(peer.name.orEmpty())
                Column(Modifier.weight(1f)) {
                    Text(peer.name ?: stringResource(R.string.bt_peer_unknown), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = when {
                            peer.state == PeerState.Failed -> peer.message ?: stringResource(R.string.bt_failed)
                            peer.state == PeerState.Exchanging -> stringResource(R.string.bt_requesting)
                            peer.state == PeerState.Waiting -> stringResource(R.string.bt_waiting)
                            peer.state == PeerState.Done -> stringResource(R.string.bt_done)
                            peer.name == null -> stringResource(R.string.bt_reading_name)
                            else -> stringResource(signalLabel(peer.rssi))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (peer.state) {
                            PeerState.Failed -> MaterialTheme.colorScheme.error
                            PeerState.Done -> LocalStatusColors.current.success
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            when (peer.state) {
                PeerState.Exchanging, PeerState.Waiting -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.End).size(24.dp), strokeWidth = 2.dp)
                PeerState.Done -> PengWaIcon(PengWaIcons.Success, modifier = Modifier.align(Alignment.End), tint = LocalStatusColors.current.success)
                else -> OutlinedButton(onClick = onExchange, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                    Text(stringResource(if (peer.state == PeerState.Failed) R.string.bt_retry else R.string.bt_request))
                }
            }
        }
    }
}

private fun signalLabel(rssi: Int): Int = when {
    rssi >= -60 -> R.string.bt_signal_strong
    rssi >= -75 -> R.string.bt_signal_medium
    else -> R.string.bt_signal_weak
}
