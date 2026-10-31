package app.pengwa.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayNameRulesTest {
    @Test
    fun `ASCII 和中文通过`() {
        assertNull(validateDisplayName("Yuki"))
        assertNull(validateDisplayName("小熊 Nana"))
    }

    @Test
    fun `空白和空字符串是 Empty`() {
        assertEquals(NameError.Empty, validateDisplayName(""))
        assertEquals(NameError.Empty, validateDisplayName("   　 "))
    }

    @Test
    fun `按 UTF-8 字节限长 - 中文每字 3 字节`() {
        assertNull(validateDisplayName("一".repeat(10))) // 30 字节
        assertEquals(NameError.TooLong, validateDisplayName("一".repeat(11))) // 33 字节
    }

    @Test
    fun `按 UTF-8 字节限长 - emoji 每个 4 字节`() {
        assertNull(validateDisplayName("😀".repeat(8))) // 32 字节，刚好
        assertEquals(NameError.TooLong, validateDisplayName("😀".repeat(9)))
    }

    @Test
    fun `首尾空白不计入长度`() {
        assertNull(validateDisplayName("  " + "a".repeat(32) + "  "))
    }

    @Test
    fun `拒绝控制字符和换行`() {
        assertEquals(NameError.InvalidChar, validateDisplayName("a\nb"))
        assertEquals(NameError.InvalidChar, validateDisplayName("a\u0000b"))
        assertEquals(NameError.InvalidChar, validateDisplayName("a\tb"))
    }

    @Test
    fun `拒绝零宽字符和方向控制符`() {
        assertEquals(NameError.InvalidChar, validateDisplayName("a​b")) // 零宽空格
        assertEquals(NameError.InvalidChar, validateDisplayName("a‍b")) // ZWJ
        assertEquals(NameError.InvalidChar, validateDisplayName("a‮b")) // 从右到左覆盖
        assertEquals(NameError.InvalidChar, validateDisplayName("a﻿b")) // BOM / 零宽不换行空格
    }

    @Test
    fun `拒绝看起来是空白的字符`() {
        assertEquals(NameError.InvalidChar, validateDisplayName("aㅤb")) // 韩文填充符
        assertEquals(NameError.InvalidChar, validateDisplayName("a⠀b")) // 盲文空白
        assertEquals(NameError.InvalidChar, validateDisplayName("a　b")) // 中间的全角空格
        assertEquals(NameError.InvalidChar, validateDisplayName("a b")) // 不换行空格
    }

    @Test
    fun `普通空格和带变体选择符的 emoji 允许`() {
        assertNull(validateDisplayName("a b"))
        assertNull(validateDisplayName("❤️"))
    }

    @Test
    fun `规范化后分解形式与合成形式等价`() {
        assertEquals("é", normalizeDisplayName("é"))
    }

    @Test
    fun `落单的代理项被拒绝`() {
        assertEquals(NameError.InvalidChar, validateDisplayName("a\uD83Db"))
    }
}
