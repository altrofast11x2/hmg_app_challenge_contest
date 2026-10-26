package com.altrostudio.drivelog.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 간격·크기 값. **임시값**이며 design/tokens의 drivelog_ 토큰이 올라오면 그 값으로 바꾼다 (design/REQUESTS.md).
 * 화면 코드는 dp 값을 직접 쓰지 않고 여기만 참조한다.
 */
object Dimens {
    val screenPadding = 32.dp
    val sectionGap = 24.dp
    val itemGap = 16.dp
    val smallGap = 8.dp

    /** 차량 화면의 최소 터치 높이 */
    val touchTarget = 72.dp

    /** 정차 후 분류 버튼 높이 */
    val purposeButtonHeight = 120.dp

    val cardPadding = 24.dp

    /** 이 폭보다 좁으면 2분할(Portrait) 레이아웃 */
    val compactWidth = 900.dp

    /** 운행기록부 표의 날짜·사용자 열 폭 */
    val logbookDateColumn = 160.dp

    /** 연결 코드 칸 (design/specs/A02-connect.md 기준) */
    val codeCellWidth = 112.dp
    val codeCellHeight = 144.dp
    val codeGroupGap = 40.dp
    val keypadKeyHeight = 128.dp
    val statusLineHeight = 48.dp
    val cellBorder = 2.dp
    val cellBorderActive = 4.dp
}
