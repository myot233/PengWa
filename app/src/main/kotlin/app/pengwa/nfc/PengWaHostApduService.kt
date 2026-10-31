package app.pengwa.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import app.pengwa.ble.encodeIdentity
import app.pengwa.exchange.ExchangeMode
import app.pengwa.pengWa

/**
 * 被碰一方：系统在对方 SELECT 我们的某个 AID 时拉起本服务（屏幕需亮着）。
 *  - 私有 AID：两边都装了 App 时的双向交换，见 [ApduHandler]；
 *  - 标准 NDEF AID：当作一张标签被任何手机读取，见 [T4tEmulator]。
 * 两者都只在主页停在 NFC 页时响应；其余时候别人碰不到我（返回"文件未找到"）。
 */
class PengWaHostApduService : HostApduService() {
    private lateinit var router: CardRouter

    override fun onCreate() {
        super.onCreate()
        val app = pengWa
        val nfcPageActive = { app.exchangeMode.isActive(ExchangeMode.Nfc) }
        val exchange = ApduHandler(
            identityProvider = { app.identityRepository.load() },
            onPeer = { peer -> app.meetings.record(peer, "NFC（对方碰过来）") },
            armed = nfcPageActive,
        )
        val tag = T4tEmulator(
            message = {
                app.identityRepository.load()?.let { NdefMessages.mimeMessage(encodeIdentity(it)) }
            },
            armed = nfcPageActive,
        )
        router = CardRouter(exchange, tag)
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray =
        router.process(commandApdu)

    override fun onDeactivated(reason: Int) {
        router.reset()
    }
}
