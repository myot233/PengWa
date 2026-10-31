package app.pengwa.ble

import app.pengwa.identity.DISPLAY_NAME_MAX_BYTES
import app.pengwa.identity.Identity
import app.pengwa.identity.normalizeDisplayName
import app.pengwa.identity.toBytes
import app.pengwa.identity.uuidFromBytes
import app.pengwa.identity.validateDisplayName
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.UUID

/**
 * 碰碰娃的蓝牙数据协议（完整说明见 docs/protocol.md）。纯 Kotlin，不依赖 Android，方便单元测试，
 * 以后做 iOS 版时照着这份规格重新实现即可。
 */
object BleProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("da9c7844-c076-4b1e-9a5f-b9433bc5bac2")

    /** 只读：对方读取我的名片。要求对方先发出申请，并且我**同意**了。 */
    val IDENTITY_CHAR_UUID: UUID = UUID.fromString("f5181d1f-bfb7-44d6-bc07-6e14fa5eb21a")

    /** 只写：对方把它的名片写给我，等于向我**申请交换**。 */
    val EXCHANGE_CHAR_UUID: UUID = UUID.fromString("9826f54e-83fb-4b2c-8fad-a9714333290e")

    /** 只读：一个字节，我对这个连接发来的申请做了什么决定，见 [ExchangeStatus]。申请方轮询它。 */
    val STATUS_CHAR_UUID: UUID = UUID.fromString("a27c16b7-0d98-4c22-b9bc-02ae6ed62b39")

    /** 只读：只返回显示名，用于"附近的人"列表。不需要先出示名片，不含 ID。 */
    val PREVIEW_CHAR_UUID: UUID = UUID.fromString("8ce4c75f-2186-46c3-8cbb-0878f1776ab4")

    const val VERSION: Byte = 1
    private const val ID_SIZE = 16
    private const val HEADER_SIZE = 1 + ID_SIZE
}

/** 名片编码：`[版本 1 字节][GUID 16 字节][UTF-8 显示名]`，最长 17 + 32 = 49 字节。 */
fun encodeIdentity(identity: Identity): ByteArray {
    val name = normalizeDisplayName(identity.displayName).toByteArray(Charsets.UTF_8)
    require(name.size <= DISPLAY_NAME_MAX_BYTES) { "显示名超过 $DISPLAY_NAME_MAX_BYTES 字节" }
    return ByteBuffer.allocate(1 + 16 + name.size)
        .put(BleProtocol.VERSION)
        .put(identity.id.toBytes())
        .put(name)
        .array()
}

/**
 * 解码对方发来的名片。对方的数据不可信，任何不合法的情况都返回 null：
 * 版本不对、长度不够、名字超长、UTF-8 非法，或名字没通过本地同一套 [validateDisplayName] 规则。
 */
fun decodeIdentity(bytes: ByteArray?): Identity? {
    if (bytes == null || bytes.size <= 17) return null
    if (bytes[0] != BleProtocol.VERSION) return null
    val nameBytes = bytes.size - 17
    if (nameBytes > DISPLAY_NAME_MAX_BYTES) return null

    val id = uuidFromBytes(bytes.copyOfRange(1, 17))
    val name = decodeValidName(bytes, 17, nameBytes) ?: return null
    return Identity(id, name)
}

/** 预览编码：`[版本 1 字节][UTF-8 显示名]`。只用于"附近的人"列表，不含 ID。 */
fun encodePreview(displayName: String): ByteArray {
    val name = normalizeDisplayName(displayName).toByteArray(Charsets.UTF_8)
    require(name.size <= DISPLAY_NAME_MAX_BYTES) { "显示名超过 $DISPLAY_NAME_MAX_BYTES 字节" }
    return byteArrayOf(BleProtocol.VERSION) + name
}

/** 解码对方的预览，不可信数据，规则与 [decodeIdentity] 里的名字完全相同；不合法返回 null。 */
fun decodePreview(bytes: ByteArray?): String? {
    if (bytes == null || bytes.size < 2 || bytes[0] != BleProtocol.VERSION) return null
    val nameBytes = bytes.size - 1
    if (nameBytes > DISPLAY_NAME_MAX_BYTES) return null
    return decodeValidName(bytes, 1, nameBytes)
}

/**
 * 严格按 UTF-8 解出名字，并要求它已经是规范形式、且通过显示名规则。
 * 要求"已规范化"是为了让同一个名字只有一种字节表示，对方没按协议编码就拒绝。
 */
private fun decodeValidName(bytes: ByteArray, offset: Int, length: Int): String? {
    val name = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, length))
            .toString()
    } catch (_: CharacterCodingException) {
        return null
    }
    if (name != normalizeDisplayName(name)) return null
    if (validateDisplayName(name) != null) return null
    return name
}
