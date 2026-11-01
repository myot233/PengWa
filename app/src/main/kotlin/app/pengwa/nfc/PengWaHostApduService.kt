package app.pengwa.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import app.pengwa.ble.encodeIdentity
import app.pengwa.pengWa

/**
 * 被碰一方：系统在对方 SELECT 我们的某个 AID 时拉起本服务（屏幕需亮着）。
 *  - 私有 AID：两边都装了 App 时的双向交换，见 [ApduHandler]；
 *  - 标准 NDEF AID：当作一张标签被任何手机读取，见 [T4tEmulator]。
 * 两者都只在 NFC 页的“出示”角色响应；其余时候拒绝命令并清掉旧会话。
 */
class PengWaHostApduService : HostApduService() {
    private lateinit var router: CardRouter

    override fun onCreate() {
        super.onCreate()
        val app = pengWa
        val displaying = { app.exchangeMode.nfcDisplaySession != null }
        val exchange = ApduHandler(
            identityProvider = { app.identityRepository.load() },
            onPeer = { peer -> app.meetings.record(peer, "NFC（对方碰过来）") },
        )
        val tag = T4tEmulator(
            message = {
                app.identityRepository.load()?.let { NdefMessages.mimeMessage(encodeIdentity(it)) }
            },
            armed = displaying,
        )
        router = CardRouter(exchange, tag, session = { app.exchangeMode.nfcDisplaySession })
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val response = router.process(commandApdu)
        if (ApduProtocol.isSelect(commandApdu) || ApduProtocol.isExchange(commandApdu)) {
            val aid = ApduProtocol.commandData(commandApdu)
            val stage = when {
                ApduProtocol.isExchange(commandApdu) -> "EXCHANGE"
                aid?.contentEquals(ApduProtocol.AID) == true -> "SELECT private AID"
                aid?.contentEquals(T4tEmulator.AID) == true -> "SELECT NDEF AID"
                else -> "SELECT other AID"
            }
            Log.d("PengWaNfc", "$stage success=${ApduProtocol.isSuccess(response)} role=${pengWa.exchangeMode.current}")
        }
        return response
    }

    override fun onDeactivated(reason: Int) {
        router.reset()
    }
}
