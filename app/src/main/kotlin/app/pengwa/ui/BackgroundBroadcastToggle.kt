package app.pengwa.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pengwa.R
import app.pengwa.ble.BlePermissions
import app.pengwa.pengWa

/** 后台广播需要的权限：广播 + 连接（Android 12+），以及发通知（Android 13+，用来提醒"有人想和你交换"）。 */
private fun broadcastPermissions(): Array<String> =
    BlePermissions.requiredForBroadcast() +
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

private fun hasBroadcastPermissions(context: Context): Boolean = broadcastPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * 设置里的"后台蓝牙广播"开关（常开蓝牙）。默认关闭，第一次打开时才申请权限、请求开启系统蓝牙。
 * 打开后，即使不打开碰碰娃，附近的人也能发现你并向你**申请交换**，你同意后才会交换；
 * 通知栏会常驻一条通知（这是安卓对后台长期广播的要求）。开关显示的是"实际生效"的状态。
 */
@Composable
fun BackgroundBroadcastToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { context.pengWa.bleExchange }
    var wanted by remember { mutableStateOf(controller.isBackgroundBroadcastEnabled()) }
    var permitted by remember { mutableStateOf(hasBroadcastPermissions(context)) }
    var denied by remember { mutableStateOf(false) }
    val bluetoothOn by controller.bluetoothOn.collectAsStateWithLifecycle()

    DisposableEffect(controller) {
        val stopObserving = controller.observeBackgroundBroadcast { wanted = it }
        onDispose(stopObserving)
    }
    // 从系统设置回来后重新检查权限和蓝牙状态
    LifecycleResumeEffect(Unit) {
        permitted = hasBroadcastPermissions(context)
        if (permitted) denied = false
        controller.refresh()
        onPauseOrDispose { }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.refresh()
    }
    fun requestEnableBluetooth() {
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
    fun turnOn() {
        denied = false
        controller.setBackgroundBroadcast(true)
        if (!bluetoothOn) requestEnableBluetooth()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permitted = result.values.all { it }
        denied = !permitted
        if (permitted) turnOn()
    }

    val checked = wanted && permitted
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bg_setting_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.bg_setting_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { turnOnRequested ->
                    when {
                        !turnOnRequested -> controller.setBackgroundBroadcast(false)
                        hasBroadcastPermissions(context) -> turnOn()
                        else -> permissionLauncher.launch(broadcastPermissions())
                    }
                },
            )
        }
        when {
            denied && !permitted -> StatusRow(
                message = stringResource(R.string.bg_permission_needed),
                actionLabel = stringResource(R.string.bt_open_app_settings),
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }
            checked && !bluetoothOn -> StatusRow(
                message = stringResource(R.string.bt_off),
                actionLabel = stringResource(R.string.bt_turn_on),
                onAction = ::requestEnableBluetooth,
            )
            checked -> Text(
                text = stringResource(R.string.bg_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusRow(message: String, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
