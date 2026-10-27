package com.altrostudio.drivelog.domain.logbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class LogbookPeriodTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `한 달 전체 기간은 달로 알아보고, 하루라도 빠지면 날짜 기간이다`() {
        assertEquals(YearMonth.of(2026, 10), LogbookPeriod.ofMonth(YearMonth.of(2026, 10)).wholeMonth)
        assertEquals(LogbookPeriod(date(2, 1, 2028), date(2, 29, 2028)), LogbookPeriod.ofMonth(YearMonth.of(2028, 2)))
        assertNull(LogbookPeriod(date(10, 1), date(10, 30)).wholeMonth)
        assertNull(LogbookPeriod(date(10, 3), date(10, 17)).wholeMonth)
    }

    @Test
    fun `기간 범위는 시작 날짜 0시부터 끝 날짜 다음 날 0시 전까지다`() {
        val (from, to) = LogbookPeriod(date(10, 26), date(10, 26)).range(zone)
        assertEquals(date(10, 26).atStartOfDay(zone).toInstant().toEpochMilli(), from)
        assertEquals(date(10, 27).atStartOfDay(zone).toInstant().toEpochMilli(), to)
    }

    @Test
    fun `달력에서 거꾸로 고르면 바로잡고, 366일을 넘으면 고를 수 없다`() {
        assertEquals(LogbookPeriod(date(10, 3), date(10, 17)), LogbookPeriod.pick(date(10, 17), date(10, 3)))
        assertEquals(366L, LogbookPeriod.pick(date(10, 1, 2025), date(10, 1, 2026))?.days)
        assertNull(LogbookPeriod.pick(date(10, 1, 2025), date(10, 2, 2026)))
    }

    @Test
    fun `CSV 파일 이름은 한 달이면 달, 아니면 기간을 쓴다 (관리자 웹과 같다)`() {
        val summary = LogbookSummary(totalKm = 0.0, commuteKm = 0.0, businessKm = 0.0, tripCount = 0, unclassifiedCount = 0)
        assertEquals("DriveLog_2026-10.csv", LogbookCsv.fileName(Logbook(LogbookPeriod.ofMonth(YearMonth.of(2026, 10)), emptyList(), summary)))
        assertEquals(
            "DriveLog_2026-10-03_2026-10-17.csv",
            LogbookCsv.fileName(Logbook(LogbookPeriod(date(10, 3), date(10, 17)), emptyList(), summary))
        )
    }
}
