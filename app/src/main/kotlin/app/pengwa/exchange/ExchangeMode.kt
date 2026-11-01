package app.pengwa.exchange

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 我的名片提供 NFC 出示／读取和二维码，附近娃娃单独启用蓝牙。
 * [None] 表示没有前台交换会话（集邮册、实验室、退后台、息屏、正在改名）。
 */
enum class ExchangeMode { None, NfcDisplay, NfcRead, Bluetooth }

/**
 * 当前交换方式的全应用共享状态。界面写入，NFC 服务、蓝牙总控、前台调度读取。
 * HCE 服务来自系统线程，所以读取用 [current]（volatile 语义由 StateFlow 保证）。
 */
class ExchangeModeState {
    private val _mode = MutableStateFlow(ExchangeMode.None)
    val mode: StateFlow<ExchangeMode> = _mode.asStateFlow()
    private var displayGeneration = 0L

    /** 当前出示会话编号；未出示时为 null。界面线程写入，HCE 可跨线程读取。 */
    @Volatile
    var nfcDisplaySession: Long? = null
        private set

    val current: ExchangeMode get() = _mode.value

    fun set(mode: ExchangeMode) {
        if (_mode.value == mode) return
        nfcDisplaySession = if (mode == ExchangeMode.NfcDisplay) ++displayGeneration else null
        _mode.value = mode
    }

    fun isActive(mode: ExchangeMode): Boolean = _mode.value == mode
}
