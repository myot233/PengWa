package app.pengwa.ble

import android.content.Context
import android.content.SharedPreferences

/**
 * "后台蓝牙广播（常开蓝牙）"的用户设置。默认关闭。
 * 打开后由前台服务 [BleBroadcastService] 在后台持续广播，别人扫到后可以向我申请交换，同意后才会交换。
 */
object BroadcastSettings {
    private const val PREFS = "settings"
    private const val KEY = "background_broadcast"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY, enabled).apply()
    }

    /** 监听开关变化，返回取消监听的函数（SharedPreferences 只弱引用监听器，调用方要保留它）。 */
    fun observe(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (key == KEY) onChange(shared.getBoolean(KEY, false))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
