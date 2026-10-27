package com.altrostudio.drivelog.domain.logbook

import com.altrostudio.drivelog.data.trip.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LogbookCsvTest {

    private fun row(note: String?, purpose: TripPurpose = TripPurpose.BUSINESS) = LogbookRow(
        tripId = "t1",
        date = LocalDate.of(2026, 10, 26),
        driver = "기본 운전자",
        startOdometerKm = 100.0,
        endOdometerKm = 112.3,
        distanceKm = 12.3,
        commuteKm = 0.0,
        businessKm = 12.3,
        note = note,
        purpose = purpose
    )

    private fun logbook(vararg rows: LogbookRow) = Logbook(
        period = LogbookPeriod.ofMonth(YearMonth.of(2026, 10)),
        rows = rows.toList(),
        summary = LogbookSummary(totalKm = 12.3, commuteKm = 0.0, businessKm = 12.3, tripCount = rows.size, unclassifiedCount = 0)
    )

    private fun lines(csv: String) = csv.removePrefix(LogbookCsv.BOM).split("\r\n")

    @Test
    fun `Excel에서 한글이 깨지지 않도록 UTF-8 BOM으로 시작하고 CRLF로 줄을 나눈다`() {
        val csv = LogbookCsv.build(logbook(row("고객사 A")))
        assertTrue(csv.startsWith(LogbookCsv.BOM))
        assertTrue(csv.contains("\r\n"))
        assertTrue(lines(csv).first().startsWith("사용일자,사용자,주행 전 계기판 거리(km)"))
    }

    @Test
    fun `한 줄은 국세청 서식 순서대로 쓰고 거리는 소수 첫째 자리까지 쓴다`() {
        val csv = LogbookCsv.build(logbook(row("고객사 A")))
        assertEquals("2026-10-26(월),기본 운전자,100.0,112.3,12.3,0.0,12.3,고객사 A,일반업무", lines(csv)[1])
    }

    @Test
    fun `쉼표·따옴표·줄바꿈이 든 비고는 따옴표로 감싼다`() {
        val csv = LogbookCsv.build(logbook(row("서울 \"본사\", 3층\n회의")))
        assertTrue(csv.contains("\"서울 \"\"본사\"\", 3층\n회의\""))
    }

    @Test
    fun `수식으로 시작하는 글자는 Excel이 실행하지 않게 막는다`() {
        val csv = LogbookCsv.build(logbook(row("=HYPERLINK(\"x\")")))
        assertTrue(csv.contains("'=HYPERLINK"))
    }

    @Test
    fun `맨 아래에 월 요약을 붙인다`() {
        val csv = LogbookCsv.build(logbook(row("고객사 A")))
        assertTrue(csv.contains("총 주행거리(km),12.3\r\n"))
        assertTrue(csv.contains("업무용 사용거리(km),12.3\r\n"))
        assertTrue(csv.contains("업무사용비율(%),100.0\r\n"))
    }
}
