package app.pengwa.nfc

/**
 * 最小的 NDEF 编解码：只处理"一条短记录、媒体类型"的消息，这正是碰碰娃标签的全部内容。
 * 纯 Kotlin，不依赖 Android。
 */
object NdefMessages {
    /** 标签里记录的 MIME 类型；App 的 NDEF_DISCOVERED 过滤器匹配的就是它。 */
    const val MIME_TYPE = "application/vnd.pengwa.card"

    // 记录头：MB(消息开始) | ME(消息结束) | SR(短记录，长度 1 字节) | TNF=2(媒体类型)
    private const val HEADER_SHORT_MIME: Int = 0x80 or 0x40 or 0x10 or 0x02
    private const val MASK_TNF = 0x07
    private const val FLAG_SR = 0x10
    private const val FLAG_IL = 0x08
    private const val TNF_MIME = 0x02

    /** 把 [payload]（名片字节）包装成一条 NDEF 消息。 */
    fun mimeMessage(payload: ByteArray): ByteArray {
        val type = MIME_TYPE.toByteArray(Charsets.US_ASCII)
        require(payload.size <= 255) { "短记录的负载最多 255 字节" }
        return byteArrayOf(HEADER_SHORT_MIME.toByte(), type.size.toByte(), payload.size.toByte()) + type + payload
    }

    /** 解析 [mimeMessage] 生成的消息，返回（类型, 负载）；格式不符返回 null。 */
    fun parseShortMimeRecord(message: ByteArray): Pair<String, ByteArray>? {
        if (message.size < 3) return null
        val header = message[0].toInt() and 0xFF
        if (header and MASK_TNF != TNF_MIME) return null
        if (header and FLAG_SR == 0 || header and FLAG_IL != 0) return null
        val typeLength = message[1].toInt() and 0xFF
        val payloadLength = message[2].toInt() and 0xFF
        if (message.size != 3 + typeLength + payloadLength) return null
        val type = String(message, 3, typeLength, Charsets.US_ASCII)
        return type to message.copyOfRange(3 + typeLength, message.size)
    }
}
