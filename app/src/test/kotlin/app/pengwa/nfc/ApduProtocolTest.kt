package app.pengwa.nfc

import app.pengwa.ble.encodeIdentity
import app.pengwa.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApduProtocolTest {
    private val alice = Identity(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"), "Alice 小熊")
    private val bob = Identity(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002"), "Bob")

    private fun handlerFor(me: Identity?, received: MutableList<Identity> = mutableListOf()) =
        ApduHandler(identityProvider = { me }, onPeer = { received += it })

    // ------------------------------------------------------------ 命令格式

    @Test
    fun `SELECT 命令格式正确`() {
        val expected = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) + ApduProtocol.AID + byteArrayOf(0x00)
        assertContentEquals(expected, ApduProtocol.selectCommand())
    }

    @Test
    fun `AID 是 F0 加 PENGWA 的 ASCII`() {
        assertEquals("F050454E475741", ApduProtocol.AID.joinToString("") { "%02X".format(it) })
        assertEquals("PENGWA", String(ApduProtocol.AID.copyOfRange(1, 7), Charsets.US_ASCII))
    }

    @Test
    fun `EXCHANGE 命令把名片放在数据域`() {
        val card = encodeIdentity(alice)
        val command = ApduProtocol.exchangeCommand(card)
        assertEquals(0x80.toByte(), command[0])
        assertEquals(0xE0.toByte(), command[1])
        assertContentEquals(card, ApduProtocol.commandData(command))
    }

    @Test
    fun `数据域长度与实际不符时解析失败`() {
        val command = ApduProtocol.exchangeCommand(encodeIdentity(alice))
        assertNull(ApduProtocol.commandData(command.copyOf(command.size - 5)))
        assertNull(ApduProtocol.commandData(byteArrayOf(0x80.toByte(), 0xE0.toByte(), 0, 0, 0)))
    }

    // ------------------------------------------------------------ 被碰一方的处理

    @Test
    fun `SELECT 我们的 AID 成功`() {
        assertContentEquals(ApduProtocol.SW_OK, handlerFor(alice).process(ApduProtocol.selectCommand()))
    }

    @Test
    fun `SELECT 别的 AID 返回文件未找到`() {
        val other = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x02, 0x01, 0x02, 0x00)
        assertContentEquals(ApduProtocol.SW_FILE_NOT_FOUND, handlerFor(alice).process(other))
    }

    @Test
    fun `没 SELECT 就 EXCHANGE 被拒绝`() {
        val response = handlerFor(alice).process(ApduProtocol.exchangeCommand(encodeIdentity(bob)))
        assertContentEquals(ApduProtocol.SW_CONDITIONS_NOT_SATISFIED, response)
    }

    @Test
    fun `非法名片不会换到我的名片`() {
        val handler = handlerFor(alice)
        handler.process(ApduProtocol.selectCommand())
        val garbage = ApduProtocol.exchangeCommand(byteArrayOf(1, 2, 3, 4, 5))
        assertContentEquals(ApduProtocol.SW_WRONG_DATA, handler.process(garbage))
    }

    @Test
    fun `名字带零宽字符的名片被拒绝且不回调`() {
        val received = mutableListOf<Identity>()
        val handler = handlerFor(alice, received)
        handler.process(ApduProtocol.selectCommand())

        val evilName = "a​b".toByteArray(Charsets.UTF_8)
        val card = byteArrayOf(1) + UUID.randomUUID().let {
            java.nio.ByteBuffer.allocate(16).putLong(it.mostSignificantBits).putLong(it.leastSignificantBits).array()
        } + evilName
        assertContentEquals(ApduProtocol.SW_WRONG_DATA, handler.process(ApduProtocol.exchangeCommand(card)))
        assertTrue(received.isEmpty())
    }

    @Test
    fun `reset 之后需要重新 SELECT`() {
        val handler = handlerFor(alice)
        handler.process(ApduProtocol.selectCommand())
        handler.reset()
        val response = handler.process(ApduProtocol.exchangeCommand(encodeIdentity(bob)))
        assertContentEquals(ApduProtocol.SW_CONDITIONS_NOT_SATISFIED, response)
    }

    @Test
    fun `未知指令和过短命令返回对应错误`() {
        val handler = handlerFor(alice)
        assertContentEquals(ApduProtocol.SW_WRONG_LENGTH, handler.process(byteArrayOf(0x00, 0x01)))
        assertContentEquals(ApduProtocol.SW_INS_NOT_SUPPORTED, handler.process(byteArrayOf(0x00, 0x01, 0x00, 0x00)))
        assertContentEquals(ApduProtocol.SW_CLA_NOT_SUPPORTED, handler.process(byteArrayOf(0x55, 0x01, 0x00, 0x00)))
    }

    // ------------------------------------------------------------ 模式开关：不在 NFC 页别人就碰不到我

    @Test
    fun `没停在 NFC 页时私有协议不响应 返回文件未找到`() {
        val handler = ApduHandler(identityProvider = { alice }, onPeer = { }, armed = { false })
        assertContentEquals(ApduProtocol.SW_FILE_NOT_FOUND, handler.process(ApduProtocol.selectCommand()))
    }

    @Test
    fun `没停在 NFC 页时发起方碰过去得到 NotCompatible`() {
        val handler = ApduHandler(identityProvider = { alice }, onPeer = { }, armed = { false })
        assertEquals(ExchangeResult.NotCompatible, runInitiator(bob) { handler.process(it) })
    }

    @Test
    fun `没停在 NFC 页时不会记录对方 也不会交出我的名片`() {
        val received = mutableListOf<Identity>()
        val handler = ApduHandler(identityProvider = { alice }, onPeer = { received += it }, armed = { false })
        runInitiator(bob) { handler.process(it) }
        assertTrue(received.isEmpty())
    }

    @Test
    fun `切回 NFC 页后恢复响应`() {
        var armed = false
        val handler = ApduHandler(identityProvider = { alice }, onPeer = { }, armed = { armed })
        assertEquals(ExchangeResult.NotCompatible, runInitiator(bob) { handler.process(it) })
        armed = true
        assertEquals(ExchangeResult.Peer(alice), runInitiator(bob) { handler.process(it) })
    }

    // ------------------------------------------------------------ 两端对接：一次完整的碰触

    @Test
    fun `一次碰触双方互换名片`() {
        val bobReceived = mutableListOf<Identity>()
        val bobCard = handlerFor(bob, bobReceived) // Bob 的手机被碰（HCE）

        // Alice 的手机去碰：命令直接交给 Bob 的处理器，等价于 IsoDep.transceive
        val result = runInitiator(alice) { command -> bobCard.process(command) }

        assertEquals(ExchangeResult.Peer(bob), result)   // Alice 拿到 Bob 的名片
        assertEquals(listOf(alice), bobReceived)         // Bob 也拿到了 Alice 的名片
    }

    @Test
    fun `碰到的不是碰碰娃时返回 NotCompatible`() {
        // 模拟公交卡：任何命令都回 6A82
        val result = runInitiator(alice) { ApduProtocol.SW_FILE_NOT_FOUND }
        assertEquals(ExchangeResult.NotCompatible, result)
    }

    @Test
    fun `对方拒绝时返回带状态码的失败`() {
        var step = 0
        val result = runInitiator(alice) {
            step++
            if (step == 1) ApduProtocol.SW_OK else ApduProtocol.SW_WRONG_DATA
        }
        assertIs<ExchangeResult.Failed>(result)
        assertTrue("6A80" in result.reason)
    }

    @Test
    fun `对方返回的名片不合法时失败`() {
        var step = 0
        val result = runInitiator(alice) {
            step++
            if (step == 1) ApduProtocol.SW_OK else byteArrayOf(1, 2, 3, 0x90.toByte(), 0x00)
        }
        assertIs<ExchangeResult.Failed>(result)
    }

    @Test
    fun `最长名片的往返不超过短 APDU 上限`() {
        val longName = Identity(UUID.randomUUID(), "一".repeat(10) + "ab")
        val command = ApduProtocol.exchangeCommand(encodeIdentity(longName))
        assertTrue(command.size <= 5 + 255 + 1)
        val bobCard = handlerFor(bob) // 同一次碰触里要用同一个会话，SELECT 的状态才会保留
        val result = runInitiator(longName) { bobCard.process(it) }
        assertEquals(ExchangeResult.Peer(bob), result)
    }
}
