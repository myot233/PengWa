package app.pengwa.ui

import android.Manifest
import android.content.Intent
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pengwa.R
import app.pengwa.ble.BroadcastSettings
import app.pengwa.notification.PengWaNotification

/**
 * 常驻通知开关（正式版可见）。默认关闭，用户第一次打开时才申请通知权限。
 * 开关显示的是"实际生效"的状态：用户想开，并且系统也允许。
 */
@Composable
fun NotificationToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var wanted by remember { mutableStateOf(PengWaNotification.isEnabled(context)) }
    var permitted by remember { mutableStateOf(PengWaNotification.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }

    // 在通知栏划掉、别处改了设置时，开关跟着变
    DisposableEffect(context) {
        val stopObserving = PengWaNotification.observeEnabled(context) { wanted = it }
        onDispose(stopObserving)
    }
    // 从系统设置回来后重新检查权限（用户可能在那里开了或撤销了）
    LifecycleResumeEffect(Unit) {
        permitted = PengWaNotification.hasPermission(context)
        if (permitted) denied = false
        onPauseOrDispose { }
    }

    // 后台蓝牙广播开着时，这条通知就是前台服务的通知，不能关：开关锁定为开
    var forcedByBroadcast by remember { mutableStateOf(BroadcastSettings.isEnabled(context)) }
    DisposableEffect(context) {
        val stopObserving = BroadcastSettings.observe(context) { forcedByBroadcast = it }
        onDispose(stopObserving)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        denied = !granted
        if (granted) PengWaNotification.setEnabled(context, true)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.notif_setting_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(if (forcedByBroadcast) R.string.notif_forced_by_broadcast else R.string.notif_setting_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = forcedByBroadcast || (wanted && permitted),
                enabled = !forcedByBroadcast,
                onCheckedChange = { turnOn ->
                    when {
                        !turnOn -> PengWaNotification.setEnabled(context, false)
                        PengWaNotification.hasPermission(context) -> {
                            denied = false
                            PengWaNotification.setEnabled(context, true)
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        if (denied && !permitted) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.notif_permission_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) {
                    Text(stringResource(R.string.notif_open_settings))
                }
            }
        }
    }
}
