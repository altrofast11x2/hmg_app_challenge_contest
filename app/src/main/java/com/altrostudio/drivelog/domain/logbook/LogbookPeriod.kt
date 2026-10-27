package com.altrostudio.drivelog.domain.logbook

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 운행기록부 기간 (끝 날짜 포함). 달 칩을 고르면 그 달 전체, 달력에서 고르면 고른 날짜들이다.
 * 관리자 웹(`web/js/logbook.js`의 periodRange·pickPeriod)과 같은 규칙이다.
 */
data class LogbookPeriod(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "끝 날짜가 시작 날짜보다 빠릅니다: $start ~ $end" }
    }

    /** 한 달 전체면 그 달, 아니면 null */
    val wholeMonth: YearMonth?
        get() = YearMonth.from(start).takeIf { start.dayOfMonth == 1 && end == it.atEndOfMonth() }

    /** 시작·끝 날짜를 모두 넣은 날 수 */
    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    /** 그 기간에 시작한 운행을 고르는 범위 [from, to) (epoch 밀리초) */
    fun range(zone: ZoneId): Pair<Long, Long> =
        start.atStartOfDay(zone).toInstant().toEpochMilli() to end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        /** 한 번에 고를 수 있는 날 수 (운행기록부 한 사업연도 정도) */
        const val MAX_DAYS = 366L

        fun ofMonth(month: YearMonth) = LogbookPeriod(month.atDay(1), month.atEndOfMonth())

        /** 달력에서 고른 두 날짜. 순서가 바뀌었으면 바로잡고, [MAX_DAYS]를 넘으면 null */
        fun pick(first: LocalDate, second: LocalDate): LogbookPeriod? {
            val period = if (first <= second) LogbookPeriod(first, second) else LogbookPeriod(second, first)
            return period.takeIf { it.days <= MAX_DAYS }
        }
    }
}
