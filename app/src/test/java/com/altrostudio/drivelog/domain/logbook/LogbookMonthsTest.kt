package com.altrostudio.drivelog.domain.logbook

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class LogbookMonthsTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val current = YearMonth.of(2026, 10)

    private fun at(year: Int, month: Int, day: Int = 15) = ZonedDateTime.of(year, month, day, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `운행이 없어도 최근 1년을 이번 달까지 보여 준다`() {
        val choices = LogbookMonths.choices(emptyList(), current, zone)
        assertEquals(12, choices.size)
        assertEquals(YearMonth.of(2025, 11), choices.first().month)
        assertEquals(current, choices.last().month)
    }

    @Test
    fun `달마다 운행 수를 센다 (한국 시간 기준)`() {
        // 10월 1일 0시 30분(한국 시간)은 UTC로는 9월 30일이지만 10월로 센다
        val earlyOctober = ZonedDateTime.of(2026, 10, 1, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        val choices = LogbookMonths.choices(listOf(at(2026, 10), earlyOctober, at(2026, 8)), current, zone)
        assertEquals(2, choices.first { it.month == current }.tripCount)
        assertEquals(1, choices.first { it.month == YearMonth.of(2026, 8) }.tripCount)
        assertEquals(0, choices.first { it.month == YearMonth.of(2026, 9) }.tripCount)
    }

    @Test
    fun `1년보다 오래된 운행이 있으면 그 달부터, 최대 2년까지`() {
        assertEquals(YearMonth.of(2025, 3), LogbookMonths.choices(listOf(at(2025, 3)), current, zone).first().month)
        val choices = LogbookMonths.choices(listOf(at(2020, 1)), current, zone)
        assertEquals(24, choices.size)
        assertEquals(YearMonth.of(2024, 11), choices.first().month)
    }
}
