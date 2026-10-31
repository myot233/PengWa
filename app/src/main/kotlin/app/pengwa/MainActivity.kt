package app.pengwa

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.CardEmulation
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.pengwa.nfc.NdefLaunchParser
import app.pengwa.nfc.NfcTagExchanger
import app.pengwa.nfc.PengWaHostApduService
import app.pengwa.ui.PengWaRoot
import app.pengwa.ui.theme.PengWaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 通知栏按钮带来的"请切到某一页"请求；界面处理完后清空。 */
    private var requestedTab by mutableStateOf<Int?>(null)

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val nfcExchanger by lazy { NfcTagExchanger(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            requestedTab = tabFrom(intent)
            handleNdefLaunch(intent)
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

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
        setPreferredCardService(true)
    }

    override fun onPause() {
        super.onPause()
        setPreferredCardService(false)
        runCatching { nfcAdapter?.disableForegroundDispatch(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { requestedTab = it }
        // 先看是不是被别人的标签拉起的；不是的话，才当作前台调度送来的标签
        if (!handleNdefLaunch(intent)) {
            IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)?.let(::onTagDiscovered)
        }
    }

    /**
     * 别人的手机读了我们的标签、系统把本 App 拉起：记下对方，并趁手机还贴着，
     * 在同一张"卡"上尝试跑私有协议把我的名片也交给对方（对方装了 App 才会成功，失败无妨）。
     */
    private fun handleNdefLaunch(intent: Intent): Boolean {
        val peer = NdefLaunchParser.peerFrom(intent) ?: return false
        pengWa.meetings.record(peer, "NFC 标签（系统拉起）")
        IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)?.let(::onTagDiscovered)
        return true
    }

    /**
     * 前台时让本页接收 IsoDep 标签，并保持手机默认的"既读又被读"状态：
     * 不用 Reader Mode，因为它会关掉本机的卡模拟，两台手机都开就谁也读不到谁。
     */
    private fun enableNfcForegroundDispatch() {
        val adapter = nfcAdapter?.takeIf { it.isEnabled } ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        adapter.enableForegroundDispatch(this, pending, null, arrayOf(arrayOf(IsoDep::class.java.name)))
    }

    /**
     * 前台时声明"我们的 AID 归本 App 的服务"。标准 NDEF 标签 AID（D2760000850101）不是我们独占的，
     * 系统自带的"嵌入式标签"服务等也可能登记它；前台首选能保证本 App 在前台时由我们响应，离开前台自动撤销。
     */
    private fun setPreferredCardService(enable: Boolean) {
        val adapter = nfcAdapter?.takeIf { it.isEnabled } ?: return
        val service = ComponentName(this, PengWaHostApduService::class.java)
        runCatching {
            val emulation = CardEmulation.getInstance(adapter)
            if (enable) emulation.setPreferredService(this, service) else emulation.unsetPreferredService(this)
        }
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
