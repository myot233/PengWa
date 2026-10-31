package app.pengwa.ble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.pengwa.notification.PengWaNotification
import app.pengwa.pengWa

/**
 * 后台蓝牙广播的前台服务。安卓不允许应用在后台长期广播，必须挂一条常驻通知并声明前台服务类型；
 * 这条通知就是主页"设置"里的常驻通知（自定义布局），多一行"蓝牙广播中"。
 *
 * 服务本身不做蓝牙操作：只在启动和销毁时通知 [BleExchangeController] 重新决定该广播还是停止。
 * 后台只广播、接受申请，不扫描，所以很省电。重启手机或在设置里强行停止后不会自动恢复，需要再打开一次应用。
 */
class BleBroadcastService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = PengWaNotification.build(this)
        if (notification == null) {
            // 还没设置显示名，没有名片可以广播
            stopSelf()
            return
        }
        try {
            ServiceCompat.startForeground(
                this,
                PengWaNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            // 比如缺少蓝牙权限、或系统不允许此时启动前台服务：关掉开关，免得界面显示"已开启"却没在工作
            Log.w(TAG, "无法启动前台服务", e)
            BroadcastSettings.setEnabled(this, false)
            stopSelf()
            return
        }
        isRunning = true
        pengWa.bleExchange.refresh()
    }

    // 被系统因内存回收后会自动重启，重启时 intent 为 null，onCreate 会重新走一遍
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false
        pengWa.bleExchange.refresh()
        PengWaNotification.refresh(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PengWaBroadcast"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BleBroadcastService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BleBroadcastService::class.java))
        }

        /** 设置是开着的但服务没在跑（比如进程被杀后用户重新打开了应用）：补启动。必须在应用前台时调用。 */
        fun ensureRunning(context: Context) {
            if (isRunning || !BroadcastSettings.isEnabled(context)) return
            if (!BlePermissions.grantedForBroadcast(context)) return
            if (context.pengWa.identityRepository.load() == null) return
            start(context)
        }
    }
}
