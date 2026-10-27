package com.altrostudio.drivelog.domain.logbook

import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class LogbookBuilderTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val month = LogbookPeriod.ofMonth(YearMonth.of(2026, 10))

    private fun trip(id: String, day: Int, startKm: Double, endKm: Double, purpose: TripPurpose, note: String? = null) = TripEntity(
        id = id,
        vehicleId = "V1",
        status = TripStatus.COMPLETED,
        startedAt = LocalDate.of(2026, 10, day).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
        lastSeenAt = 0,
        startOdometerKm = startKm,
        endOdometerKm = endKm,
        purpose = purpose,
        visitNote = note,
        updatedAt = 0
    )

    @Test
    fun `출퇴근·일반업무는 업무용 열에, 비업무·미분류는 주행거리에만 들어간다`() {
        val logbook = LogbookBuilder.build(
            month,
            listOf(
                trip("a", 1, 100.0, 112.0, TripPurpose.COMMUTE),
                trip("b", 2, 112.0, 142.0, TripPurpose.BUSINESS, note = "고객사 A"),
                trip("c", 3, 142.0, 152.0, TripPurpose.PERSONAL),
                trip("d", 4, 152.0, 160.0, TripPurpose.UNCLASSIFIED)
            ),
            zone,
            driverName = "기본 운전자"
        )

        assertEquals(listOf(12.0, 0.0, 0.0, 0.0), logbook.rows.map { it.commuteKm })
        assertEquals(listOf(0.0, 30.0, 0.0, 0.0), logbook.rows.map { it.businessKm })
        assertEquals("고객사 A", logbook.rows[1].note)
        assertEquals(LocalDate.of(2026, 10, 2), logbook.rows[1].date)

        val summary = logbook.summary
        assertEquals(60.0, summary.totalKm, 0.001)
        assertEquals(42.0, summary.workKm, 0.001)
        assertEquals(70.0, summary.workRatioPercent!!, 0.001)
        assertEquals(1, summary.unclassifiedCount)
        // 홈의 용도별 막대: 출퇴근 12 + 일반업무 30 + 비업무 10 + 미분류 8 = 60
        assertEquals(10.0, summary.personalKm, 0.001)
        assertEquals(8.0, summary.unclassifiedKm, 0.001)
    }

    @Test
    fun `운행 중에 계기판이 줄었으면(교체·초기화) 거리를 0으로 보고 합계에서 빼지 않는다`() {
        val logbook = LogbookBuilder.build(
            month,
            listOf(trip("a", 1, 100.0, 120.0, TripPurpose.BUSINESS), trip("b", 2, 208.0, 3.0, TripPurpose.BUSINESS)),
            zone,
            driverName = "기본 운전자"
        )
        assertEquals(listOf(20.0, 0.0), logbook.rows.map { it.distanceKm })
        assertEquals(20.0, logbook.summary.totalKm, 0.001)
    }

    @Test
    fun `운행이 없으면 업무사용비율은 계산하지 않는다`() {
        val summary = LogbookBuilder.build(month, emptyList(), zone, "기본 운전자").summary
        assertEquals(0.0, summary.totalKm, 0.0)
        assertNull(summary.workRatioPercent)
    }

    @Test
    fun `월 범위는 그 달 1일 0시부터 다음 달 1일 0시 전까지다`() {
        val (from, to) = month.range(zone)
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli(), from)
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli(), to)
    }
}
