package app.pengwa.ble

import app.pengwa.identity.Identity
import app.pengwa.identity.toBytes
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BleProtocolTest {
    private val id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")

    private fun raw(name: ByteArray, version: Byte = BleProtocol.VERSION) =
        byteArrayOf(version) + id.toBytes() + name

    @Test
    fun `编码格式 版本 + 16 字节 GUID + UTF-8 名字`() {
        val bytes = encodeIdentity(Identity(id, "小熊"))
        assertEquals(1, bytes[0].toInt())
        assertContentEquals(id.toBytes(), bytes.copyOfRange(1, 17))
        assertContentEquals("小熊".toByteArray(Charsets.UTF_8), bytes.copyOfRange(17, bytes.size))
    }

    @Test
    fun `编码再解码得到同一个名片`() {
        val original = Identity(id, "Yuki 小熊 🐻")
        assertEquals(original, decodeIdentity(encodeIdentity(original)))
    }

    @Test
    fun `最长名片不超过 49 字节`() {
        val bytes = encodeIdentity(Identity(id, "a".repeat(32)))
        assertEquals(49, bytes.size)
    }

    @Test
    fun `编码超长名字直接报错`() {
        assertFailsWith<IllegalArgumentException> {
            encodeIdentity(Identity(id, "a".repeat(33)))
        }
    }

    @Test
    fun `null 空数组和只有头部都解不出来`() {
        assertNull(decodeIdentity(null))
        assertNull(decodeIdentity(ByteArray(0)))
        assertNull(decodeIdentity(raw(ByteArray(0)))) // 名字为空
    }

    @Test
    fun `版本号不对时拒绝`() {
        assertNull(decodeIdentity(raw("abc".toByteArray(), version = 2)))
    }

    @Test
    fun `非法 UTF-8 拒绝`() {
        assertNull(decodeIdentity(raw(byteArrayOf(0xC3.toByte(), 0x28)))) // 无效的两字节序列
        assertNull(decodeIdentity(raw(byteArrayOf(0xFF.toByte()))))
    }

    @Test
    fun `对方发来的名字超长时拒绝`() {
        assertNull(decodeIdentity(raw("a".repeat(33).toByteArray())))
    }

    @Test
    fun `对方发来带控制字符或零宽字符的名字时拒绝`() {
        assertNull(decodeIdentity(raw("a\nb".toByteArray())))
        assertNull(decodeIdentity(raw("a​b".toByteArray())))
        assertNull(decodeIdentity(raw("a‮b".toByteArray())))
    }

    @Test
    fun `对方发来未规范化或带首尾空白的名字时拒绝`() {
        assertNull(decodeIdentity(raw(" abc".toByteArray()))) // 首部空格
        assertNull(decodeIdentity(raw("é".toByteArray()))) // 分解形式，应为 NFC
    }
}
