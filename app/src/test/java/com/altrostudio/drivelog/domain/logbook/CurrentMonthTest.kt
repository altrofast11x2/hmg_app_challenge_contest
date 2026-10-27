package com.altrostudio.drivelog.domain.logbook

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class CurrentMonthTest {

    @Test
    fun `앱을 켜 둔 채 한국 시간으로 자정이 지나 달이 바뀌면 새 달을 한 번만 알린다`() = runTest {
        // 2026-10-31 23:58 한국 시간부터 1분씩
        var now = Instant.parse("2026-10-31T14:58:00Z").toEpochMilli()
        val clock = { now.also { now += 60_000L } }

        val months = currentMonthFlow(ZoneId.of("Asia/Seoul"), clock).take(2).toList()

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months)
    }
}
