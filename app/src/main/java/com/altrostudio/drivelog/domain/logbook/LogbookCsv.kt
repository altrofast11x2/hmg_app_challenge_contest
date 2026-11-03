package com.altrostudio.drivelog.domain.logbook

import com.altrostudio.drivelog.data.trip.TripPurpose
import java.time.format.TextStyle
import java.util.Locale

/**
 * 월별 운행기록부 CSV. Excel에서 한글이 깨지지 않도록 UTF-8 BOM을 붙이고 줄바꿈은 CRLF로 쓴다.
 * 열은 국세청 업무용승용차 운행기록부 서식 순서를 따른다 (서식 최신 여부는 사용자가 따로 확인).
 */
object LogbookCsv {

    const val BOM = "\uFEFF"

    private val HEADER = listOf(
        "사용일자", "사용자", "주행 전 계기판 거리(km)", "주행 후 계기판 거리(km)", "주행거리(km)",
        "업무용 사용거리 출퇴근용(km)", "업무용 사용거리 일반업무용(km)", "비고(방문처·업무 내용)", "구분"
    )

    private val PURPOSE_LABELS = mapOf(
        TripPurpose.UNCLASSIFIED to "미분류",
        TripPurpose.COMMUTE to "출퇴근",
        TripPurpose.BUSINESS to "일반업무",
        TripPurpose.PERSONAL to "비업무"
    )

    /** 한 달 전체면 DriveLog_2026-10.csv, 아니면 DriveLog_2026-10-03_2026-10-17.csv (관리자 웹과 같다) */
    fun fileName(logbook: Logbook): String = logbook.period.let { period ->
        period.wholeMonth?.let { "DriveLog_$it.csv" } ?: "DriveLog_${period.start}_${period.end}.csv"
    }

    fun build(logbook: Logbook): String {
        val rows = logbook.rows.map { row ->
            listOf(
                "${row.date}(${row.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})",
                row.driver,
                km(row.startOdometerKm),
                km(row.endOdometerKm),
                km(row.distanceKm),
                km(row.commuteKm),
                km(row.businessKm),
                row.note.orEmpty(),
                PURPOSE_LABELS.getValue(row.purpose)
            )
        }
        val summary = logbook.summary
        val footer = listOf(
            emptyList(),
            listOf("총 주행거리(km)", km(summary.totalKm)),
            listOf("업무용 사용거리(km)", km(summary.workKm)),
            listOf("업무사용비율(%)", summary.workRatioPercent?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty()),
            listOf("미분류 운행(건)", summary.unclassifiedCount.toString())
        )
        return BOM + (listOf(HEADER) + rows + footer).joinToString("\r\n", postfix = "\r\n") { line ->
            line.joinToString(",") { escape(it) }
        }
    }

    private fun km(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty()

    /** 쉼표·따옴표·줄바꿈은 따옴표로 감싸고, =·+·-·@로 시작하는 글자는 Excel 수식으로 실행되지 않게 막는다 */
    private fun escape(value: String): String {
        val safe = if (value.firstOrNull() in FORMULA_PREFIXES && value.toDoubleOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
}
