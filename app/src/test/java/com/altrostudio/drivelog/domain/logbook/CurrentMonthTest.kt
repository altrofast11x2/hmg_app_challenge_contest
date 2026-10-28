package com.altrostudio.drivelog.domain.logbook

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentMonthTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `앱을 켜 둔 채 한국 시간으로 자정이 지나 달이 바뀌면 새 달을 한 번만 알린다`() = runTest {
        // 2026-10-31 23:58 한국 시간부터, 테스트 시계를 따라간다
        val start = Instant.parse("2026-10-31T14:58:00Z").toEpochMilli()
        val clock = { start + testScheduler.currentTime }

        val months = currentMonthFlow(seoul, clock).take(2).toList()

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months)
        // 11월 1일 0시에 바로 바뀐다
        assertEquals(2 * 60_000L, testScheduler.currentTime)
    }

    @Test
    fun `달이 바뀌려면 멀었으면 한 시간에 한 번만 시계를 본다`() = runTest {
        // 2026-10-10 12:00 한국 시간
        val start = Instant.parse("2026-10-10T03:00:00Z").toEpochMilli()
        var reads = 0
        val clock = { reads++; start + testScheduler.currentTime }

        currentMonthFlow(seoul, clock).launchIn(backgroundScope)
        advanceTimeBy(3 * 60 * 60_000L)

        assertTrue("3시간 동안 시계를 $reads 번 봤다", reads <= 4)
    }
}
