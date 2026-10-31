package app.pengwa.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.pengwa.MainActivity
import app.pengwa.R
import app.pengwa.ble.IncomingRequest
import app.pengwa.pengWa

/**
 * "有人想和你交换名片"的通知：应用在后台时用它提醒，带"同意 / 拒绝"两个按钮。
 * 同意按钮要求先解锁手机，避免在锁屏状态下被人随手点掉。应用在前台时不发通知，直接弹对话框。
 */
object RequestNotifier {
    const val ACTION_ACCEPT = "app.pengwa.action.ACCEPT_REQUEST"
    const val ACTION_DECLINE = "app.pengwa.action.DECLINE_REQUEST"
    const val EXTRA_KEY = "key"

    private const val CHANNEL_ID = "pengwa_requests"
    private const val BASE_ID = 1000

    fun show(context: Context, request: IncomingRequest) {
        if (!PengWaNotification.hasPermission(context)) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        ensureChannel(appContext, manager)
        val id = notificationId(request.key)

        val open = PendingIntent.getActivity(
            appContext,
            id,
            Intent(appContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ME)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val accept = NotificationCompat.Action.Builder(
            0,
            appContext.getString(R.string.request_accept),
            actionIntent(appContext, request.key, ACTION_ACCEPT, id),
        ).setAuthenticationRequired(true).build()
        val decline = NotificationCompat.Action.Builder(
            0,
            appContext.getString(R.string.request_decline),
            actionIntent(appContext, request.key, ACTION_DECLINE, id + 1),
        ).build()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pengwa)
            .setContentTitle(appContext.getString(R.string.request_title, request.peer.displayName))
            .setContentText(appContext.getString(R.string.request_notif_text))
            .setContentIntent(open)
            .addAction(accept)
            .addAction(decline)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .build()
        manager.notify(id, notification)
    }

    fun cancel(context: Context, key: String) {
        context.applicationContext.getSystemService(NotificationManager::class.java).cancel(notificationId(key))
    }

    private fun actionIntent(context: Context, key: String, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RequestActionReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 同意和拒绝要用不同的 requestCode，所以通知 ID 之间留出间隔。 */
    private fun notificationId(key: String): Int = BASE_ID + (key.hashCode() and 0x3FFF) * 2

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.request_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.request_channel_desc) },
        )
    }
}

/** 通知上"同意 / 拒绝"按钮的处理。决定交给蓝牙总控，它负责回复对方并把对方记入集邮册。 */
class RequestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(RequestNotifier.EXTRA_KEY) ?: return
        val accept = intent.action == RequestNotifier.ACTION_ACCEPT
        context.pengWa.bleExchange.respond(key, accept)
    }
}
