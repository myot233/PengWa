package app.pengwa.nfc

import app.pengwa.ble.decodeIdentity
import app.pengwa.ble.encodeIdentity
import app.pengwa.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class T4tEmulatorTest {
    private val alice = Identity(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"), "Alice 小熊")
    private val bob = Identity(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002"), "Bob")

    private fun message(identity: Identity?) = identity?.let { NdefMessages.mimeMessage(encodeIdentity(it)) }

    private fun emulator(identity: Identity? = alice, armed: Boolean = true) =
        T4tEmulator(message = { message(identity) }, armed = { armed })

    // ---- 读卡方用到的 APDU 构造 ----

    private fun selectAid(aid: ByteArray) =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    private fun selectFile(id: Int) =
        byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, (id shr 8).toByte(), id.toByte())

    private fun readBinary(offset: Int, length: Int) =
        byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), length.toByte())

    private fun ByteArray.isOk() = ApduProtocol.isSuccess(this)
    private fun ByteArray.body() = copyOfRange(0, size - 2)

    /** 一个按 NFC Forum Type 4 规范读标签的"标准读卡器"，返回 NDEF 消息；任何一步失败返回 null。 */
    private fun readTag(emulator: T4tEmulator): ByteArray? {
        if (!emulator.process(selectAid(T4tEmulator.AID)).isOk()) return null
        if (!emulator.process(selectFile(T4tEmulator.FILE_CC)).isOk()) return null
        val cc = emulator.process(readBinary(0, 15))
        if (!cc.isOk() || cc.body().size != 15) return null
        if (!emulator.process(selectFile(T4tEmulator.FILE_NDEF)).isOk()) return null
        val lengthBytes = emulator.process(readBinary(0, 2))
        if (!lengthBytes.isOk()) return null
        val length = ((lengthBytes.body()[0].toInt() and 0xFF) shl 8) or (lengthBytes.body()[1].toInt() and 0xFF)

        val message = ArrayList<Byte>()
        var offset = 2
        while (message.size < length) {
            val chunk = minOf(T4tEmulator.MAX_READ, length - message.size)
            val response = emulator.process(readBinary(offset, chunk))
            if (!response.isOk()) return null
            message += response.body().toList()
            offset += response.body().size
        }
        return message.toByteArray()
    }

    // ---- 数据格式 ----

    @Test
    fun `能力文件 15 字节且声明了 E104 为只读 NDEF 文件`() {
        val cc = T4tEmulator.CAPABILITY_CONTAINER
        assertEquals(15, cc.size)
        assertEquals(15, ((cc[0].toInt() and 0xFF) shl 8) or (cc[1].toInt() and 0xFF)) // CCLEN
        assertEquals(0x20, cc[2].toInt())                                             // 映射版本 2.0
        assertEquals(0x04, cc[7].toInt())                                             // NDEF 文件控制 TLV
        assertEquals(0x06, cc[8].toInt())
        assertEquals(0xE104, ((cc[9].toInt() and 0xFF) shl 8) or (cc[10].toInt() and 0xFF))
        assertEquals(0x00, cc[13].toInt())                                            // 读：不限制
        assertEquals(0xFF, cc[14].toInt() and 0xFF)                                   // 写：禁止
    }

    @Test
    fun `NDEF 记录的字节与手工推算一致`() {
        val payload = byteArrayOf(1, 2, 3)
        val type = "application/vnd.pengwa.card".toByteArray()
        val expected = byteArrayOf(0xD2.toByte(), type.size.toByte(), 3) + type + payload
        assertContentEquals(expected, NdefMessages.mimeMessage(payload))
        assertEquals(27, type.size)
    }

    @Test
    fun `NDEF 记录编码再解析得到同样的类型和负载`() {
        val payload = encodeIdentity(alice)
        val (type, parsed) = assertNotNull(NdefMessages.parseShortMimeRecord(NdefMessages.mimeMessage(payload)))
        assertEquals(NdefMessages.MIME_TYPE, type)
        assertContentEquals(payload, parsed)
    }

    @Test
    fun `畸形的 NDEF 记录解析返回 null`() {
        assertNull(NdefMessages.parseShortMimeRecord(byteArrayOf(0xD2.toByte(), 5)))               // 太短
        assertNull(NdefMessages.parseShortMimeRecord(byteArrayOf(0xD1.toByte(), 1, 1, 0x61, 0x62))) // TNF 不是媒体类型
        assertNull(NdefMessages.parseShortMimeRecord(byteArrayOf(0xD2.toByte(), 1, 9, 0x61, 0x62))) // 长度对不上
    }

    // ---- 标准读卡器完整读一遍 ----

    @Test
    fun `标准读卡器能完整读出标签并解出名片`() {
        val message = assertNotNull(readTag(emulator()))
        val (type, payload) = assertNotNull(NdefMessages.parseShortMimeRecord(message))
        assertEquals(NdefMessages.MIME_TYPE, type)
        assertEquals(alice, decodeIdentity(payload))
    }

    @Test
    fun `最长的名片也能分多次读完`() {
        val longName = Identity(UUID.randomUUID(), "一".repeat(10) + "ab") // 32 字节
        val message = assertNotNull(readTag(emulator(longName)))
        assertTrue(message.size > T4tEmulator.MAX_READ, "需要分多次读取才有意义")
        val (_, payload) = assertNotNull(NdefMessages.parseShortMimeRecord(message))
        assertEquals(longName, decodeIdentity(payload))
    }

    // ---- 开关与边界 ----

    @Test
    fun `没打开展示开关时读卡器看不到这张标签`() {
        val closed = emulator(armed = false)
        assertContentEquals(ApduProtocol.SW_FILE_NOT_FOUND, closed.process(selectAid(T4tEmulator.AID)))
        assertNull(readTag(closed))
    }

    @Test
    fun `还没设置名字时也不响应`() {
        assertNull(readTag(emulator(identity = null)))
    }

    @Test
    fun `展示开关在读取过程中关闭 已开始的读取仍能读完`() {
        var armed = true
        val emulator = T4tEmulator(message = { message(alice) }, armed = { armed })
        assertTrue(emulator.process(selectAid(T4tEmulator.AID)).isOk())
        armed = false
        assertTrue(emulator.process(selectFile(T4tEmulator.FILE_CC)).isOk())
    }

    @Test
    fun `读取中途名字被改 也不会读出前后不一致的内容`() {
        var current = alice
        val emulator = T4tEmulator(message = { message(current) }, armed = { true })
        emulator.process(selectAid(T4tEmulator.AID))
        emulator.process(selectFile(T4tEmulator.FILE_CC))
        emulator.process(selectFile(T4tEmulator.FILE_NDEF)) // 此刻内容被定格
        current = bob
        val length = emulator.process(readBinary(0, 2)).body()
        val expectedLength = message(alice)!!.size
        assertEquals(expectedLength, ((length[0].toInt() and 0xFF) shl 8) or (length[1].toInt() and 0xFF))
    }

    @Test
    fun `未选择应用就选文件或读取会被拒绝`() {
        val emulator = emulator()
        assertContentEquals(ApduProtocol.SW_CONDITIONS_NOT_SATISFIED, emulator.process(selectFile(T4tEmulator.FILE_CC)))
        assertContentEquals(ApduProtocol.SW_CONDITIONS_NOT_SATISFIED, emulator.process(readBinary(0, 2)))
    }

    @Test
    fun `选择不存在的文件返回文件未找到`() {
        val emulator = emulator()
        emulator.process(selectAid(T4tEmulator.AID))
        assertContentEquals(ApduProtocol.SW_FILE_NOT_FOUND, emulator.process(selectFile(0xE105)))
    }

    @Test
    fun `读取超出文件长度的偏移返回参数错误`() {
        val emulator = emulator()
        emulator.process(selectAid(T4tEmulator.AID))
        emulator.process(selectFile(T4tEmulator.FILE_CC))
        assertContentEquals(byteArrayOf(0x6B, 0x00), emulator.process(readBinary(100, 10)))
    }

    @Test
    fun `标签是只读的 写入被拒绝`() {
        val emulator = emulator()
        emulator.process(selectAid(T4tEmulator.AID))
        emulator.process(selectFile(T4tEmulator.FILE_NDEF))
        val update = byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x00, 0x02, 0x00, 0x00)
        assertContentEquals(byteArrayOf(0x69, 0x82.toByte()), emulator.process(update))
    }

    // ---- 两个 AID 的路由 ----

    private fun router(identity: Identity? = alice, armed: Boolean = true, received: MutableList<Identity> = mutableListOf()) =
        CardRouter(
            exchange = ApduHandler(identityProvider = { identity }, onPeer = { received += it }),
            tag = T4tEmulator(message = { message(identity) }, armed = { armed }),
        )

    @Test
    fun `同一个服务既能被当标签读 也能走私有协议互换`() {
        val received = mutableListOf<Identity>()
        val router = router(alice, received = received)

        // 先被当成标签读取
        val asTag = T4tEmulator(message = { message(alice) }, armed = { true })
        assertNotNull(readTag(asTag)) // 对照：独立的模拟器可以读

        // 通过路由器：先走标签流程
        assertTrue(router.process(selectAid(T4tEmulator.AID)).isOk())
        assertTrue(router.process(selectFile(T4tEmulator.FILE_CC)).isOk())

        // 再换到私有 AID：之前的标签会话被清掉，私有协议照常工作
        val result = runInitiator(bob) { router.process(it) }
        assertEquals(ExchangeResult.Peer(alice), result)
        assertEquals(listOf(bob), received)
    }

    @Test
    fun `路由到标签后 私有协议的 EXCHANGE 不会被误处理`() {
        val router = router()
        router.process(selectAid(T4tEmulator.AID))
        val response = router.process(ApduProtocol.exchangeCommand(encodeIdentity(bob)))
        assertTrue(!ApduProtocol.isSuccess(response), "标签应用不应响应 EXCHANGE 命令")
    }

    @Test
    fun `未知 AID 返回文件未找到 且没有选中任何应用时拒绝后续命令`() {
        val router = router()
        assertContentEquals(ApduProtocol.SW_FILE_NOT_FOUND, router.process(selectAid(byteArrayOf(1, 2, 3, 4, 5))))
        assertContentEquals(ApduProtocol.SW_CONDITIONS_NOT_SATISFIED, router.process(readBinary(0, 2)))
    }

    @Test
    fun `展示开关关闭时 标签 AID 不可见 但私有协议不受影响`() {
        val router = router(armed = false)
        assertContentEquals(ApduProtocol.SW_FILE_NOT_FOUND, router.process(selectAid(T4tEmulator.AID)))
        assertEquals(ExchangeResult.Peer(alice), runInitiator(bob) { router.process(it) })
    }
}
