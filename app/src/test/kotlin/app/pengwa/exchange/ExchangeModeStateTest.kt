package app.pengwa.exchange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExchangeModeStateTest {
    @Test
    fun `默认什么方式都不用`() {
        val state = ExchangeModeState()
        assertEquals(ExchangeMode.None, state.current)
        ExchangeMode.entries.filter { it != ExchangeMode.None }.forEach { assertFalse(state.isActive(it)) }
    }

    @Test
    fun `同一时刻只有一种方式生效`() {
        val state = ExchangeModeState()
        state.set(ExchangeMode.NfcDisplay)
        assertTrue(state.isActive(ExchangeMode.NfcDisplay))
        assertFalse(state.isActive(ExchangeMode.Bluetooth))
        assertFalse(state.isActive(ExchangeMode.Qr))

        state.set(ExchangeMode.Bluetooth)
        assertFalse(state.isActive(ExchangeMode.NfcDisplay))
        assertTrue(state.isActive(ExchangeMode.Bluetooth))
    }

    @Test
    fun `离开主页回到都不用`() {
        val state = ExchangeModeState()
        state.set(ExchangeMode.Qr)
        state.set(ExchangeMode.None)
        ExchangeMode.entries.filter { it != ExchangeMode.None }.forEach { assertFalse(state.isActive(it)) }
    }

    @Test
    fun `状态流发出最新值`() {
        val state = ExchangeModeState()
        state.set(ExchangeMode.Bluetooth)
        assertEquals(ExchangeMode.Bluetooth, state.mode.value)
    }
}
