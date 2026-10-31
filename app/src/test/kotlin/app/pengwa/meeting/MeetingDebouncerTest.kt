package app.pengwa.meeting

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeetingDebouncerTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `第一次一定记录`() {
        assertTrue(MeetingDebouncer(10_000).shouldRecord(a, 0))
    }

    @Test
    fun `窗口内重复触发不再记录`() {
        val debouncer = MeetingDebouncer(10_000)
        assertTrue(debouncer.shouldRecord(a, 1_000))
        assertFalse(debouncer.shouldRecord(a, 2_000))
        assertFalse(debouncer.shouldRecord(a, 10_999))
    }

    @Test
    fun `窗口过后再碰算新的一次`() {
        val debouncer = MeetingDebouncer(10_000)
        assertTrue(debouncer.shouldRecord(a, 0))
        assertTrue(debouncer.shouldRecord(a, 10_000))
    }

    @Test
    fun `被拒绝的触发不会顺延窗口`() {
        val debouncer = MeetingDebouncer(10_000)
        assertTrue(debouncer.shouldRecord(a, 0))
        assertFalse(debouncer.shouldRecord(a, 9_000))
        assertTrue(debouncer.shouldRecord(a, 10_000)) // 仍以第一次为准
    }

    @Test
    fun `不同的人互不影响`() {
        val debouncer = MeetingDebouncer(10_000)
        assertTrue(debouncer.shouldRecord(a, 0))
        assertTrue(debouncer.shouldRecord(b, 100))
    }
}
