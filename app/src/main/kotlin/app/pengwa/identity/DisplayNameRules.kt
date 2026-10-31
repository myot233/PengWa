package app.pengwa.identity

import java.text.Normalizer

enum class NameError { Empty, TooLong, InvalidChar }

/** 显示名按 UTF-8 编码后的字节数上限（要塞进 NFC / 蓝牙的小数据包）。约 10 个汉字或 8 个 emoji。 */
const val DISPLAY_NAME_MAX_BYTES = 32

/** 存储和传输前统一规范化：Unicode NFC 合成 + 去掉首尾空白，保证同一个名字的字节表示唯一。 */
fun normalizeDisplayName(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFC).trim()

/**
 * 校验显示名，合法返回 null，否则返回具体错误。
 *
 * 规则：
 *  - 规范化后不能为空；
 *  - UTF-8 字节数不超过 [DISPLAY_NAME_MAX_BYTES]；
 *  - 只允许可显示字符：拒绝控制字符、格式字符（零宽空格 / 连接符、从右到左覆盖符等）、
 *    换行、代理对残片、私有区、未分配码位，以及除普通空格外的所有空白，
 *    再加上几个"看起来是空白"的特例（韩文填充符、盲文空白）。
 *  - 取舍：零宽连接符 ZWJ 也属于格式字符，因此 👨‍👩‍👧 这类复合 emoji 会被拒绝。
 */
fun validateDisplayName(raw: String): NameError? {
    val name = normalizeDisplayName(raw)
    if (name.isEmpty()) return NameError.Empty
    if (name.toByteArray(Charsets.UTF_8).size > DISPLAY_NAME_MAX_BYTES) return NameError.TooLong

    var i = 0
    while (i < name.length) {
        val codePoint = name.codePointAt(i)
        if (!isDisplayable(codePoint)) return NameError.InvalidChar
        i += Character.charCount(codePoint)
    }
    return null
}

private val INVISIBLE_FILLERS = setOf(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800)

private fun isDisplayable(codePoint: Int): Boolean {
    if (codePoint == ' '.code) return true
    if (codePoint in INVISIBLE_FILLERS) return false
    return when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(),
        Character.FORMAT.toInt(),
        Character.SURROGATE.toInt(),
        Character.PRIVATE_USE.toInt(),
        Character.UNASSIGNED.toInt(),
        Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(),
        Character.SPACE_SEPARATOR.toInt(),
        -> false
        else -> true
    }
}
