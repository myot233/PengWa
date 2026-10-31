package app.pengwa.identity

import java.nio.ByteBuffer
import java.util.UUID

/** 本机身份：[id] 是永久标识（GUID），[displayName] 是用户自定义的外显名，可随时修改。 */
data class Identity(val id: UUID, val displayName: String)

/** 线上传输用的 16 字节大端编码（NFC / 蓝牙都用它，比 36 字符文本省空间）。 */
fun UUID.toBytes(): ByteArray =
    ByteBuffer.allocate(16).putLong(mostSignificantBits).putLong(leastSignificantBits).array()

fun uuidFromBytes(bytes: ByteArray): UUID {
    require(bytes.size == 16) { "UUID 需要 16 字节，实际 ${bytes.size}" }
    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long)
}
