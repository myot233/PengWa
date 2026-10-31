package app.pengwa.nfc

import app.pengwa.ble.decodeIdentity
import app.pengwa.ble.encodeIdentity
import app.pengwa.identity.Identity

/**
 * NFC（ISO-DEP / HCE）上的 APDU 协议，纯 Kotlin，完整说明见 docs/protocol.md。
 *
 * 流程：发起方 SELECT 我们的 AID → 发 EXCHANGE 命令（数据域是发起方的名片）
 *      → 对方在响应里返回它的名片。一次碰触、一次往返即完成双向交换。
 */
object ApduProtocol {
    /** 私有 AID：F0 开头表示私有应用，后面是 "PENGWA" 的 ASCII。 */
    val AID: ByteArray = byteArrayOf(0xF0.toByte(), 0x50, 0x45, 0x4E, 0x47, 0x57, 0x41)

    private const val CLA_ISO: Byte = 0x00
    private const val INS_SELECT: Byte = 0xA4.toByte()
    private const val P1_SELECT_BY_NAME: Byte = 0x04
    private const val CLA_PROPRIETARY: Byte = 0x80.toByte()
    private const val INS_EXCHANGE: Byte = 0xE0.toByte()

    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
    val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
    val SW_WRONG_DATA = byteArrayOf(0x6A, 0x80.toByte())
    val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)

    fun selectCommand(): ByteArray =
        byteArrayOf(CLA_ISO, INS_SELECT, P1_SELECT_BY_NAME, 0x00, AID.size.toByte()) + AID + byteArrayOf(0x00)

    fun exchangeCommand(card: ByteArray): ByteArray {
        require(card.size in 1..255) { "名片长度需在 1..255 字节" }
        return byteArrayOf(CLA_PROPRIETARY, INS_EXCHANGE, 0x00, 0x00, card.size.toByte()) + card + byteArrayOf(0x00)
    }

    fun isSelect(command: ByteArray): Boolean =
        command.size >= 5 && command[0] == CLA_ISO && command[1] == INS_SELECT && command[2] == P1_SELECT_BY_NAME

    fun isExchange(command: ByteArray): Boolean =
        command.size >= 4 && command[0] == CLA_PROPRIETARY && command[1] == INS_EXCHANGE

    /** 取短 APDU 的数据域；格式不对返回 null。允许末尾带一个字节的 Le。 */
    fun commandData(command: ByteArray): ByteArray? {
        if (command.size < 5) return null
        val lc = command[4].toInt() and 0xFF
        if (lc == 0) return null
        if (command.size != 5 + lc && command.size != 5 + lc + 1) return null
        return command.copyOfRange(5, 5 + lc)
    }

    fun isSuccess(response: ByteArray): Boolean =
        response.size >= 2 && response[response.size - 2] == SW_OK[0] && response[response.size - 1] == SW_OK[1]

    fun withStatus(data: ByteArray, status: ByteArray): ByteArray = data + status
}

/**
 * 被碰的一方（HCE 服务）的命令处理。不依赖 Android，便于测试。
 * 一次碰触对应一个会话：必须先 SELECT 成功，之后才接受 EXCHANGE。
 */
class ApduHandler(
    private val identityProvider: () -> Identity?,
    private val onPeer: (Identity) -> Unit,
    /** 为 false 时不响应：主页没有停在 NFC 页的时候，别人碰不到我。 */
    private val armed: () -> Boolean = { true },
) {
    private var selected = false

    fun reset() {
        selected = false
    }

    fun process(command: ByteArray): ByteArray = when {
        command.size < 4 -> ApduProtocol.SW_WRONG_LENGTH
        ApduProtocol.isSelect(command) -> handleSelect(command)
        ApduProtocol.isExchange(command) -> handleExchange(command)
        command[0] != 0x00.toByte() && command[0] != 0x80.toByte() -> ApduProtocol.SW_CLA_NOT_SUPPORTED
        else -> ApduProtocol.SW_INS_NOT_SUPPORTED
    }

    private fun handleSelect(command: ByteArray): ByteArray {
        val aid = ApduProtocol.commandData(command)
        return if (aid != null && aid.contentEquals(ApduProtocol.AID) && armed()) {
            selected = true
            ApduProtocol.SW_OK
        } else {
            selected = false
            ApduProtocol.SW_FILE_NOT_FOUND
        }
    }

    private fun handleExchange(command: ByteArray): ByteArray {
        if (!selected) return ApduProtocol.SW_CONDITIONS_NOT_SATISFIED
        // 对方必须出示合法名片才会拿到我的名片，避免被通用读卡器随手抄走
        val data = ApduProtocol.commandData(command) ?: return ApduProtocol.SW_WRONG_LENGTH
        val peer = decodeIdentity(data) ?: return ApduProtocol.SW_WRONG_DATA
        val me = identityProvider() ?: return ApduProtocol.SW_CONDITIONS_NOT_SATISFIED
        onPeer(peer)
        return ApduProtocol.withStatus(encodeIdentity(me), ApduProtocol.SW_OK)
    }
}

sealed interface ExchangeResult {
    data class Peer(val identity: Identity) : ExchangeResult

    /** 对方不是碰碰娃（比如公交卡、别的 NFC 标签），静默忽略。 */
    data object NotCompatible : ExchangeResult

    data class Failed(val reason: String) : ExchangeResult
}

/** 碰别人的一方（读卡方）的流程。[transceive] 在真机上是 IsoDep.transceive，测试里直接接 [ApduHandler]。 */
fun runInitiator(me: Identity, transceive: (ByteArray) -> ByteArray): ExchangeResult {
    val selectResponse = transceive(ApduProtocol.selectCommand())
    if (!ApduProtocol.isSuccess(selectResponse)) return ExchangeResult.NotCompatible

    val response = transceive(ApduProtocol.exchangeCommand(encodeIdentity(me)))
    if (!ApduProtocol.isSuccess(response)) {
        val status = response.takeLast(2).joinToString("") { "%02X".format(it) }
        return ExchangeResult.Failed("对方拒绝了交换（状态 $status）")
    }
    val peer = decodeIdentity(response.copyOfRange(0, response.size - 2))
        ?: return ExchangeResult.Failed("对方返回的名片不合法")
    return ExchangeResult.Peer(peer)
}
