package com.altrostudio.drivelog.domain.logbook

import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.distanceKm
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 국세청 업무용승용차 운행기록부의 한 줄 */
data class LogbookRow(
    val tripId: String,
    val date: LocalDate,
    val driver: String,
    val startOdometerKm: Double?,
    val endOdometerKm: Double?,
    val distanceKm: Double,
    /** 업무용 사용거리 중 출퇴근용 */
    val commuteKm: Double,
    /** 업무용 사용거리 중 일반 업무용 */
    val businessKm: Double,
    /** 비고(방문처) */
    val note: String?,
    val purpose: TripPurpose
)

data class LogbookSummary(
    val totalKm: Double,
    val commuteKm: Double,
    val businessKm: Double,
    val tripCount: Int,
    val unclassifiedCount: Int
) {
    /** 업무용 사용거리 = 출퇴근용 + 일반 업무용 */
    val workKm: Double get() = commuteKm + businessKm

    /** 업무사용비율(%) = 업무용 사용거리 ÷ 총 주행거리. 미분류 운행은 업무용에 넣지 않는다 */
    val workRatioPercent: Double? get() = if (totalKm > 0) workKm / totalKm * 100 else null
}

data class Logbook(val month: YearMonth, val rows: List<LogbookRow>, val summary: LogbookSummary)

/** 완료된 운행을 월별 운행기록부로 바꾼다 */
object LogbookBuilder {

    /** 그 달에 시작한 운행을 고르는 범위 [from, to) (epoch 밀리초) */
    fun monthRange(month: YearMonth, zone: ZoneId): Pair<Long, Long> =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun build(month: YearMonth, trips: List<TripEntity>, zone: ZoneId, driverName: String): Logbook {
        val rows = trips.sortedBy { it.startedAt }.map { trip ->
            val distance = trip.distanceKm() ?: 0.0
            LogbookRow(
                tripId = trip.id,
                date = Instant.ofEpochMilli(trip.startedAt).atZone(zone).toLocalDate(),
                driver = driverName,
                startOdometerKm = trip.startOdometerKm,
                endOdometerKm = trip.endOdometerKm,
                distanceKm = distance,
                commuteKm = if (trip.purpose == TripPurpose.COMMUTE) distance else 0.0,
                businessKm = if (trip.purpose == TripPurpose.BUSINESS) distance else 0.0,
                note = trip.visitNote,
                purpose = trip.purpose
            )
        }
        val summary = LogbookSummary(
            totalKm = rows.sumOf { it.distanceKm },
            commuteKm = rows.sumOf { it.commuteKm },
            businessKm = rows.sumOf { it.businessKm },
            tripCount = rows.size,
            unclassifiedCount = rows.count { it.purpose == TripPurpose.UNCLASSIFIED }
        )
        return Logbook(month, rows, summary)
    }
}
