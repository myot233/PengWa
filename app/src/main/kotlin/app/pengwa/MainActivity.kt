package app.pengwa

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pengwa.ble.BleBroadcastService
import app.pengwa.exchange.ExchangeMode
import app.pengwa.nfc.NdefLaunchParser
import app.pengwa.nfc.NfcModeController
import app.pengwa.nfc.NfcTagExchanger
import app.pengwa.ui.PengWaRoot
import app.pengwa.ui.theme.PengWaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 通知栏按钮带来的"请切到某一页"请求；界面处理完后清空。 */
    private var requestedTab by mutableStateOf<Int?>(null)

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val nfcExchanger by lazy { NfcTagExchanger(this) }

    private val nfcMode by lazy {
        NfcModeController(this) { tag ->
            runOnUiThread {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    pengWa.exchangeMode.isActive(ExchangeMode.NfcRead)
                ) onTagDiscovered(tag)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            requestedTab = tabFrom(intent)
            handleNdefLaunch(intent)
        }
        // 只有主页停在 NFC 页并且本页在前台时，才接收别人的卡；切走或退到后台立即关闭
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                pengWa.exchangeMode.mode.collect { mode -> nfcMode.apply(mode) }
            }
        }
        setContent {
            PengWaTheme {
                PengWaRoot(
                    requestedTab = requestedTab,
                    onTabRequestHandled = { requestedTab = null },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        pengWa.bleExchange.setAppForeground(true)
        // 后台广播的设置是开着的但服务没在跑（比如进程被杀后用户重新打开了应用）：补启动
        BleBroadcastService.ensureRunning(this)
    }

    override fun onStop() {
        super.onStop()
        pengWa.bleExchange.setAppForeground(false)
    }

    override fun onPause() {
        nfcMode.apply(ExchangeMode.None)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { requestedTab = it }
        handleNdefLaunch(intent)
    }

    private fun handleNdefLaunch(intent: Intent) {
        val peer = NdefLaunchParser.peerFrom(intent) ?: return
        pengWa.meetings.record(peer, "NFC 标签（系统拉起）")
    }

    private fun onTagDiscovered(tag: Tag) {
        lifecycleScope.launch(Dispatchers.IO) {
            nfcExchanger.exchange(tag)
            // 两部手机贴着不动时别反复触发，让系统在短时间内忽略这张"卡"
            runCatching { nfcAdapter?.ignore(tag, IGNORE_TAG_MILLIS, null, null) }
        }
    }

    private fun tabFrom(intent: Intent?): Int? =
        intent?.takeIf { it.hasExtra(EXTRA_TAB) }?.getIntExtra(EXTRA_TAB, TAB_ME)

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_ME = 0
        const val TAB_CONTACTS = 1
        const val TAB_LAB = 2
        private const val IGNORE_TAG_MILLIS = 2000
    }
}
