package app.pengwa.identity

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentityTest {
    @Test
    fun `GUID 转 16 字节再还原得到同一个值`() {
        val id = UUID.randomUUID()
        val bytes = id.toBytes()
        assertEquals(16, bytes.size)
        assertEquals(id, uuidFromBytes(bytes))
    }

    @Test
    fun `字节编码是大端且与已知值一致`() {
        val id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
        val expected = byteArrayOf(
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
            0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(),
            0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(),
        )
        assertContentEquals(expected, id.toBytes())
    }

    @Test
    fun `长度不是 16 字节时拒绝解码`() {
        assertFailsWith<IllegalArgumentException> { uuidFromBytes(ByteArray(15)) }
    }

    @Test
    fun `空白名字被判为 Empty`() {
        assertEquals(NameError.Empty, validateDisplayName("   "))
    }

    @Test
    fun `正常名字通过校验`() {
        assertEquals(null, validateDisplayName(" 小熊 "))
    }
}
