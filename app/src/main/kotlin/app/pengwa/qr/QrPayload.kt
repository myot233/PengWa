package app.pengwa.qr

import app.pengwa.ble.decodeIdentity
import app.pengwa.ble.encodeIdentity
import app.pengwa.identity.Identity
import java.util.Base64

/**
 * 二维码里放的内容：`pengwa://c/` + 名片字节的 base64url（无填充）。
 * 名片字节与 NFC、蓝牙用的是同一套编码，所以三种方式交换的信息完全一致。
 * 扫到的内容来自外部，解码时重新校验，不合法一律返回 null。纯 Kotlin。
 */
object QrPayload {
    const val PREFIX = "pengwa://c/"

    // 名片最长 49 字节 -> base64 约 66 字符；放宽一些但拒绝明显过长的输入
    private const val MAX_LENGTH = 200

    fun encode(identity: Identity): String =
        PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(encodeIdentity(identity))

    fun decode(text: String): Identity? {
        val trimmed = text.trim()
        if (trimmed.length > MAX_LENGTH || !trimmed.startsWith(PREFIX)) return null
        val bytes = try {
            Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX))
        } catch (_: IllegalArgumentException) {
            return null
        }
        return decodeIdentity(bytes)
    }
}
