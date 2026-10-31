package app.pengwa.nfc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagShareGateTest {
    @Test
    fun `默认关闭`() {
        assertFalse(TagShareGate().armed)
    }

    @Test
    fun `打开再关闭`() {
        val gate = TagShareGate()
        gate.arm()
        assertTrue(gate.armed)
        gate.disarm()
        assertFalse(gate.armed)
    }
}
