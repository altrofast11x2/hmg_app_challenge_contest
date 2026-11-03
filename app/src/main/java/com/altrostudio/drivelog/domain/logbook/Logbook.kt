package com.altrostudio.drivelog.domain.logbook

import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.distanceKm
import com.altrostudio.drivelog.data.trip.driverLabel
import java.time.Instant
import java.time.LocalDate
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
    /** 비고: 방문처와 업무 내용 ("방문처 · 업무 내용") */
    val note: String?,
    val purpose: TripPurpose
)

data class LogbookSummary(
    val totalKm: Double,
    val commuteKm: Double,
    val businessKm: Double,
    val tripCount: Int,
    val unclassifiedCount: Int,
    /** 비업무 거리, 미분류 거리 (홈의 용도별 막대에 쓴다) */
    val personalKm: Double = 0.0,
    val unclassifiedKm: Double = 0.0
) {
    /** 업무용 사용거리 = 출퇴근용 + 일반 업무용 */
    val workKm: Double get() = commuteKm + businessKm

    /** 업무사용비율(%) = 업무용 사용거리 ÷ 총 주행거리. 미분류 운행은 업무용에 넣지 않는다 */
    val workRatioPercent: Double? get() = if (totalKm > 0) workKm / totalKm * 100 else null
}

data class Logbook(val period: LogbookPeriod, val rows: List<LogbookRow>, val summary: LogbookSummary)

/** 완료된 운행을 운행기록부(한 달 또는 고른 기간)로 바꾼다 */
object LogbookBuilder {

    /** [driverName]은 운전자를 고르지 않은 운행에 쓰는 이름 (차량의 기본 운전자) */
    fun build(period: LogbookPeriod, trips: List<TripEntity>, zone: ZoneId, driverName: String): Logbook {
        val rows = trips.sortedBy { it.startedAt }.map { trip ->
            val distance = trip.distanceKm() ?: 0.0
            LogbookRow(
                tripId = trip.id,
                date = Instant.ofEpochMilli(trip.startedAt).atZone(zone).toLocalDate(),
                driver = trip.driverLabel() ?: driverName,
                startOdometerKm = trip.startOdometerKm,
                endOdometerKm = trip.endOdometerKm,
                distanceKm = distance,
                commuteKm = if (trip.purpose == TripPurpose.COMMUTE) distance else 0.0,
                businessKm = if (trip.purpose == TripPurpose.BUSINESS) distance else 0.0,
                note = listOfNotNull(trip.visitNote, trip.workNote).joinToString(" · ").ifEmpty { null },
                purpose = trip.purpose
            )
        }
        val summary = LogbookSummary(
            totalKm = rows.sumOf { it.distanceKm },
            commuteKm = rows.sumOf { it.commuteKm },
            businessKm = rows.sumOf { it.businessKm },
            tripCount = rows.size,
            unclassifiedCount = rows.count { it.purpose == TripPurpose.UNCLASSIFIED },
            personalKm = rows.filter { it.purpose == TripPurpose.PERSONAL }.sumOf { it.distanceKm },
            unclassifiedKm = rows.filter { it.purpose == TripPurpose.UNCLASSIFIED }.sumOf { it.distanceKm }
        )
        return Logbook(period, rows, summary)
    }
}
