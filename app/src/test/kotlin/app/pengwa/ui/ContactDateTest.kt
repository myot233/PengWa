package app.pengwa.ui

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactDateTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun millis(value: String) = Instant.parse(value).toEpochMilli()
    private val now = millis("2026-11-01T02:00:00Z")

    @Test fun todayUsesLocalTime() {
        assertEquals("今天 09:30", compactContactDate(millis("2026-11-01T01:30:00Z"), now, zone))
    }

    @Test fun yesterdayUsesCalendarDayAcrossMidnight() {
        assertEquals("昨天", compactContactDate(millis("2026-10-31T15:59:00Z"), millis("2026-10-31T16:01:00Z"), zone))
    }

    @Test fun olderDateInSameYearOmitsYear() {
        assertEquals("10 月 20 日", compactContactDate(millis("2026-10-20T02:00:00Z"), now, zone))
    }

    @Test fun olderDateInAnotherYearIncludesYear() {
        assertEquals("2025 年 11 月 1 日", compactContactDate(millis("2025-11-01T02:00:00Z"), now, zone))
    }
}
