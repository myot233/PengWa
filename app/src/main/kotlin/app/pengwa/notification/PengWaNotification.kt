package app.pengwa.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.pengwa.MainActivity
import app.pengwa.R
import app.pengwa.ble.BleBroadcastService
import app.pengwa.ble.BroadcastSettings
import app.pengwa.pengWa

/**
 * 常驻通知栏控件：用 RemoteViews 自定义折叠 / 展开两套布局，配合 DecoratedCustomViewStyle。
 * 注意：Android 12 起系统会给自定义通知加统一外框并限制高度，所以只能定制"内容区"。
 *
 * 这条通知有两种来源：用户在设置里打开的"常驻通知"，或者"后台蓝牙广播"——后者是前台服务，
 * 必须有通知。只要其中一个开着就要显示；后台广播开着时通知里会多一行"蓝牙广播中"。
 */
object PengWaNotification {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "pengwa_ongoing"
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "ongoing_notification"
    private const val DISMISS_REQUEST_CODE = 100

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        refresh(context)
    }

    /**
     * 监听"是否开启"的变化（开关切换、在通知栏划掉都会触发），返回取消监听的函数。
     * SharedPreferences 只弱引用监听器，所以监听器由返回的函数持有，调用方要保留这个函数。
     */
    fun observeEnabled(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (key == KEY_ENABLED) onChange(shared.getBoolean(KEY_ENABLED, false))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 这条通知是否应该存在：用户开了常驻通知，或者后台蓝牙广播开着（前台服务必须有通知）。 */
    fun shouldShow(context: Context): Boolean = isEnabled(context) || BroadcastSettings.isEnabled(context)

    /** 按当前设置和身份状态发出或撤掉通知；名字修改、开关切换、进程重启后都调用它。 */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val notification = if (shouldShow(appContext) && hasPermission(appContext)) build(appContext) else null
        if (notification == null) {
            manager.cancel(NOTIFICATION_ID)
        } else {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    /** 构建通知；还没设置显示名时返回 null。前台服务启动时也用它。 */
    fun build(context: Context): Notification? {
        val appContext = context.applicationContext
        val identity = appContext.pengWa.identityRepository.load() ?: return null
        ensureChannel(appContext, appContext.getSystemService(NotificationManager::class.java))

        val broadcasting = BroadcastSettings.isEnabled(appContext) && BleBroadcastService.isRunning
        val idText = appContext.getString(
            if (broadcasting) R.string.notif_id_broadcasting else R.string.notif_id_format,
            identity.id.toString().take(8),
        )
        val hint = appContext.getString(if (broadcasting) R.string.notif_hint_broadcasting else R.string.notif_hint)

        val collapsed = RemoteViews(appContext.packageName, R.layout.notification_collapsed).apply {
            setTextViewText(R.id.notif_name, identity.displayName)
            setTextViewText(R.id.notif_id, idText)
            setOnClickPendingIntent(R.id.notif_btn_contacts, openTab(appContext, MainActivity.TAB_CONTACTS))
        }
        val expanded = RemoteViews(appContext.packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.notif_name, identity.displayName)
            setTextViewText(R.id.notif_id, idText)
            setTextViewText(R.id.notif_hint, hint)
            setOnClickPendingIntent(R.id.notif_btn_me, openTab(appContext, MainActivity.TAB_ME))
            setOnClickPendingIntent(R.id.notif_btn_contacts, openTab(appContext, MainActivity.TAB_CONTACTS))
        }

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pengwa)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(openTab(appContext, MainActivity.TAB_ME))
            // Android 14+ 允许用户划掉这类通知；划掉就当作"用户关闭了常驻通知"，见 NotificationDismissReceiver
            .setDeleteIntent(dismissIntent(appContext))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun dismissIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST_CODE,
            Intent(context, NotificationDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 每个目标页用不同的 requestCode，避免 PendingIntent 被系统复用成同一个 extras。 */
    private fun openTab(context: Context, tab: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            tab,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
