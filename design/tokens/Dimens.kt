package com.altrostudio.drivelog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// DriveLog 크기·간격·모서리·동작 토큰 v0.1 — 원본: design/tokens/Dimens.kt
// (가정값) 표시: Pleos Connect Design Assets(Figma) 확인 전 임시값. 받으면 이 파일만 바뀐다

@Immutable
data class DriveLogDimens(
    // 간격 (8 기반)
    val spacingExtraSmall: Dp = 8.dp,       // drivelog_spacing_extra_small
    val spacingSmall: Dp = 16.dp,           // drivelog_spacing_small
    val spacingMedium: Dp = 24.dp,          // drivelog_spacing_medium
    val spacingLarge: Dp = 32.dp,           // drivelog_spacing_large
    val spacingExtraLarge: Dp = 48.dp,      // drivelog_spacing_extra_large
    val spacingHuge: Dp = 64.dp,            // drivelog_spacing_huge
    // 화면 여백 (앱 창 안쪽)
    val screenPaddingHorizontalLandscape: Dp = 72.dp,  // drivelog_screen_padding_horizontal_landscape (가정값)
    val screenPaddingHorizontalPortrait: Dp = 40.dp,   // drivelog_screen_padding_horizontal_portrait (가정값)
    val screenPaddingVertical: Dp = 48.dp,             // drivelog_screen_padding_vertical (가정값)
    // 모서리
    val radiusSmall: Dp = 12.dp,            // drivelog_radius_small — 배지, 작은 칩
    val radiusMedium: Dp = 20.dp,           // drivelog_radius_medium — 입력칸, 일반 버튼, 목록 행
    val radiusLarge: Dp = 32.dp,            // drivelog_radius_large — 카드, 용도 버튼
    val radiusFull: Dp = 999.dp,            // drivelog_radius_full — 알약형
    // 선
    val borderWidth: Dp = 2.dp,             // drivelog_border_width
    val selectionRingWidth: Dp = 6.dp,      // drivelog_selection_ring_width
    val selectionRingOffset: Dp = 6.dp,     // drivelog_selection_ring_offset — 버튼 바깥으로 띄우는 거리
    // 터치 영역 (가정값)
    val touchTargetMinimum: Dp = 80.dp,     // drivelog_touch_target_minimum — 모든 터치 요소의 최소 높이·너비
    val buttonHeightMedium: Dp = 96.dp,     // drivelog_button_height_medium — 일반 버튼
    val buttonHeightLarge: Dp = 120.dp,     // drivelog_button_height_large — 화면의 주 버튼 (동의, 연결)
    val purposeButtonHeightLandscape: Dp = 300.dp, // drivelog_purpose_button_height_landscape
    val purposeButtonHeightPortrait: Dp = 176.dp,  // drivelog_purpose_button_height_portrait
    val keypadKeyHeightLandscape: Dp = 128.dp,     // drivelog_keypad_key_height_landscape
    val keypadKeyHeightPortrait: Dp = 112.dp,      // drivelog_keypad_key_height_portrait
    val codeCellWidth: Dp = 112.dp,         // drivelog_code_cell_width — 연결 코드 한 칸
    val codeCellHeight: Dp = 144.dp,        // drivelog_code_cell_height
    val listRowHeight: Dp = 112.dp,         // drivelog_list_row_height — 최근 운행 한 줄
    val topBarHeight: Dp = 112.dp,          // drivelog_top_bar_height — 앱 상단 바
    val badgeHeight: Dp = 48.dp,            // drivelog_badge_height
    // 아이콘
    val iconSizeSmall: Dp = 32.dp,          // drivelog_icon_size_small
    val iconSizeMedium: Dp = 44.dp,         // drivelog_icon_size_medium
    val iconSizeLarge: Dp = 72.dp,          // drivelog_icon_size_large — 용도 버튼 아이콘
    // 레이아웃 판단
    val portraitBreakpointWidth: Dp = 1200.dp, // drivelog_portrait_breakpoint_width — 앱 창 너비가 이보다 작으면 Portrait 레이아웃
    val contentMaxWidth: Dp = 1440.dp       // drivelog_content_max_width — 긴 글(동의 문구) 최대 너비
)

// 동작 (Pleos 상호작용 규칙)
object DriveLogMotion {
    const val PRESS_SCALE = 0.95f               // drivelog_press_scale — 누르면 95%로 줄어듦
    const val PRESS_DURATION_MILLIS = 100       // drivelog_press_duration — 누름 축소 시간
    const val TOUCH_RESPONSE_LIMIT_MILLIS = 250 // drivelog_touch_response_limit — 이 안에 시각 반응
    const val LOADING_THRESHOLD_MILLIS = 2000   // drivelog_loading_threshold — 넘으면 로딩 표시
    // 주행 중에는 애니메이션 시간 0 (전환은 즉시 교체)
}

// 그림자: 다크 테마는 그림자 대신 면 색(surface → surfaceRaised)으로 높이를 표현한다
// 라이트 테마만: drivelog_elevation_card = 2dp, drivelog_elevation_dialog = 12dp
object DriveLogElevation {
    val card: Dp = 2.dp
    val dialog: Dp = 12.dp
}

val LocalDriveLogDimens = staticCompositionLocalOf { DriveLogDimens() }
