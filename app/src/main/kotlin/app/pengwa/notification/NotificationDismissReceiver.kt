package app.pengwa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 用户在通知栏把常驻通知划掉时触发（应用自己 cancel 不会触发）。
 * 划掉 = 用户不想要它了，所以把开关置为关闭，避免下次启动又自己冒出来。
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PengWaNotification.setEnabled(context, false)
    }
}
