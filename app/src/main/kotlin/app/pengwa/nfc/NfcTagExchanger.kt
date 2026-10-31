package app.pengwa.nfc

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import app.pengwa.pengWa
import java.io.IOException

/** 碰别人一方：拿到前台调度送来的 Tag，在 IsoDep 上跑 [runInitiator]。会阻塞，必须在后台线程调用。 */
class NfcTagExchanger(private val context: Context) {

    fun exchange(tag: Tag): ExchangeResult {
        val isoDep = IsoDep.get(tag) ?: return ExchangeResult.NotCompatible
        val app = context.pengWa
        val me = app.identityRepository.load()
            ?: return ExchangeResult.Failed("还没有设置显示名")

        val result = try {
            isoDep.connect()
            isoDep.timeout = TIMEOUT_MS
            runInitiator(me) { isoDep.transceive(it) }
        } catch (e: IOException) {
            // 碰的时间太短、手机移开了
            ExchangeResult.Failed("连接中断：${e.message}")
        } finally {
            runCatching { isoDep.close() }
        }

        when (result) {
            is ExchangeResult.Peer -> app.meetings.record(result.identity, "NFC（我碰过去）")
            is ExchangeResult.Failed -> Log.w(TAG, "NFC 交换失败：${result.reason}")
            ExchangeResult.NotCompatible -> Log.d(TAG, "不是碰碰娃，忽略")
        }
        return result
    }

    private companion object {
        const val TAG = "PengWaNfc"
        const val TIMEOUT_MS = 3000
    }
}
