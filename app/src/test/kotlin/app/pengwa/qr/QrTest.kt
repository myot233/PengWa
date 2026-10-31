package app.pengwa.qr

import app.pengwa.ble.encodeIdentity
import app.pengwa.identity.Identity
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrTest {
    private val alice = Identity(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"), "Alice 小熊")

    // ---- 内容格式 ----

    @Test
    fun `二维码内容是固定前缀加名片字节的 base64url`() {
        val text = QrPayload.encode(alice)
        assertTrue(text.startsWith("pengwa://c/"))
        val body = text.removePrefix("pengwa://c/")
        assertTrue('=' !in body && '+' !in body && '/' !in body, "应为无填充的 URL 安全 base64")
        assertEquals(encodeIdentity(alice).toList(), Base64.getUrlDecoder().decode(body).toList())
    }

    @Test
    fun `编码再解码得到同一个名片`() {
        assertEquals(alice, QrPayload.decode(QrPayload.encode(alice)))
    }

    @Test
    fun `首尾的空白和换行不影响解码`() {
        assertEquals(alice, QrPayload.decode("  \n" + QrPayload.encode(alice) + "\r\n"))
    }

    @Test
    fun `最长的名片生成的二维码内容在 80 个字符以内`() {
        val longest = Identity(UUID.randomUUID(), "一".repeat(10) + "ab")
        assertTrue(QrPayload.encode(longest).length <= 80)
    }

    // ---- 外部内容不可信 ----

    @Test
    fun `不是碰碰娃的二维码返回 null`() {
        assertNull(QrPayload.decode("https://example.com/"))
        assertNull(QrPayload.decode("hello"))
        assertNull(QrPayload.decode(""))
    }

    @Test
    fun `前缀对但内容坏掉的返回 null`() {
        assertNull(QrPayload.decode("pengwa://c/"))
        assertNull(QrPayload.decode("pengwa://c/!!!不是base64!!!"))
        assertNull(QrPayload.decode("pengwa://c/AAAA"))
    }

    @Test
    fun `名字带零宽字符的名片被拒绝`() {
        val evil = byteArrayOf(1) + ByteArray(16) { it.toByte() } + "a​b".toByteArray()
        val text = "pengwa://c/" + Base64.getUrlEncoder().withoutPadding().encodeToString(evil)
        assertNull(QrPayload.decode(text))
    }

    @Test
    fun `过长的内容被拒绝`() {
        assertNull(QrPayload.decode("pengwa://c/" + "A".repeat(500)))
    }

    // ---- 生成再识别：模拟相机拍到屏幕上的二维码 ----

    /** 把二维码矩阵放大成灰度图（黑 0 白 255），四周留静区，就像相机拍到的一帧亮度数据。 */
    private fun render(text: String, scale: Int = 8, quiet: Int = 4): Triple<ByteArray, Int, Int> {
        val matrix = QrEncoder.encode(text)
        val size = (matrix.width + quiet * 2) * scale
        val luma = ByteArray(size * size) { 255.toByte() }
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (!matrix[x, y]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                luma[((y + quiet) * scale + dy) * size + (x + quiet) * scale + dx] = 0
            }
        }
        return Triple(luma, size, size)
    }

    @Test
    fun `生成的二维码能被识别出来 且解出同一张名片`() {
        val (luma, w, h) = render(QrPayload.encode(alice))
        val decoded = assertNotNull(QrFrameDecoder.decode(luma, w, h))
        assertEquals(alice, QrPayload.decode(decoded))
    }

    @Test
    fun `每行带对齐填充的帧也能识别`() {
        val (luma, w, h) = render(QrPayload.encode(alice))
        val stride = w + 24 // CameraX 的 Y 平面每行可能比宽度多出几十个字节
        val padded = ByteArray(stride * h) { 127 }
        for (row in 0 until h) System.arraycopy(luma, row * w, padded, row * stride, w)
        val decoded = assertNotNull(QrFrameDecoder.decode(padded, w, h, rowStride = stride))
        assertEquals(alice, QrPayload.decode(decoded))
    }

    @Test
    fun `没有二维码的画面返回 null`() {
        val blank = ByteArray(300 * 300) { 200.toByte() }
        assertNull(QrFrameDecoder.decode(blank, 300, 300))
    }

    @Test
    fun `缩小到只有几个像素一个模块也能识别`() {
        val (luma, w, h) = render(QrPayload.encode(alice), scale = 4)
        assertNotNull(QrFrameDecoder.decode(luma, w, h))
    }
}
