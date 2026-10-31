package app.pengwa.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import app.pengwa.ble.encodeIdentity
import app.pengwa.pengWa

/**
 * 被碰一方：系统在对方 SELECT 我们的某个 AID 时拉起本服务（App 不在前台、没有运行也可以，屏幕需亮着）。
 *  - 私有 AID：两边都装了 App 时的双向交换，见 [ApduHandler]；
 *  - 标准 NDEF AID：当作一张标签被任何手机读取，见 [T4tEmulator]，仅在名片页前台时响应。
 */
class PengWaHostApduService : HostApduService() {
    private lateinit var router: CardRouter

    override fun onCreate() {
        super.onCreate()
        val app = pengWa
        val exchange = ApduHandler(
            identityProvider = { app.identityRepository.load() },
            onPeer = { peer -> app.meetings.record(peer, "NFC（对方碰过来）") },
        )
        val tag = T4tEmulator(
            message = {
                app.identityRepository.load()?.let { NdefMessages.mimeMessage(encodeIdentity(it)) }
            },
            armed = { app.tagShareGate.armed },
        )
        router = CardRouter(exchange, tag)
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray =
        router.process(commandApdu)

    override fun onDeactivated(reason: Int) {
        router.reset()
    }
}
