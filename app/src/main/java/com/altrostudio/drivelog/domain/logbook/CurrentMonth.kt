package com.altrostudio.drivelog.domain.logbook

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 지금 달. 차는 시동을 꺼도 앱을 절전 대기로 며칠씩 살려 두기도 해서, 화면을 만들 때 한 번만 정하면
 * 달이 바뀐 뒤에도 지난달을 "이번 달"로 보여 줄 수 있다 (2026-10-27 점검).
 * 다음 달이 시작되는 시각까지 기다렸다가 알린다. 백그라운드에서 타이머를 자주 돌리지 않도록 (Pleos 성능·리소스 가이드)
 * 기다리는 시간은 [maxWaitMs]를 넘지 않는다 — 그 사이 시계나 시간대가 바뀌어도 늦어도 그만큼 뒤에는 맞춘다
 */
fun currentMonthFlow(
    zone: ZoneId,
    clock: () -> Long = System::currentTimeMillis,
    maxWaitMs: Long = 60 * 60_000L
): Flow<YearMonth> = flow {
    while (true) {
        val now = Instant.ofEpochMilli(clock()).atZone(zone)
        emit(YearMonth.from(now))
        val nextMonth = now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone)
        delay(Duration.between(now, nextMonth).toMillis().coerceIn(MIN_WAIT_MS, maxWaitMs))
    }
}.distinctUntilChanged()

private const val MIN_WAIT_MS = 1_000L
