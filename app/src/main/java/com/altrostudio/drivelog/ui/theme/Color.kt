package com.altrostudio.drivelog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// DriveLog 색 토큰 v0.1 (2026-10-26) — 원본: design/tokens/Color.kt (직접 수정 금지, 디자인 쪽이 갱신)
// 토큰 이름 규칙: drivelog_text_primary  →  Kotlin DriveLogTheme.colors.textPrimary  →  CSS --drivelog-text-primary
// 무드: 깊은 남색 실내 + 샌드 톤 소재감 + 맑은 블루 액션 (특정 브랜드 색상값은 쓰지 않은 자체 팔레트)

@Immutable
data class DriveLogColorScheme(
    // 배경과 면
    val background: Color,              // drivelog_background — 앱 창 기본 배경
    val backgroundDriving: Color,       // drivelog_background_driving — 주행 중 화면 배경 (더 어둡게)
    val surface: Color,                 // drivelog_surface — 카드, 목록 면
    val surfaceRaised: Color,           // drivelog_surface_raised — 버튼, 입력칸 등 한 단 위의 면
    val surfacePressed: Color,          // drivelog_surface_pressed — 누름 상태의 면
    val border: Color,                  // drivelog_border — 구분선, 카드 테두리
    val borderStrong: Color,            // drivelog_border_strong — 입력칸 테두리, 강조 구분선
    // 글자
    val textPrimary: Color,             // drivelog_text_primary
    val textSecondary: Color,           // drivelog_text_secondary
    val textTertiary: Color,            // drivelog_text_tertiary — 보조 설명, 단위 (4.5:1 이상 확인)
    val textDisabled: Color,            // drivelog_text_disabled
    // 버튼
    val buttonPrimaryBackground: Color,         // drivelog_button_primary_background
    val buttonPrimaryBackgroundPressed: Color,  // drivelog_button_primary_background_pressed
    val buttonPrimaryText: Color,               // drivelog_button_primary_text
    val buttonSecondaryBackground: Color,       // drivelog_button_secondary_background
    val buttonSecondaryBackgroundPressed: Color,// drivelog_button_secondary_background_pressed
    val buttonSecondaryText: Color,             // drivelog_button_secondary_text
    val buttonDisabledBackground: Color,        // drivelog_button_disabled_background
    // 강조
    val accent: Color,                  // drivelog_accent — 링크, 포커스, 진행 표시
    val signature: Color,               // drivelog_signature — 샌드 톤. "지난번 선택", 선택 링, 브랜드 포인트
    val signatureText: Color,           // drivelog_signature_text — signature 위의 글자
    val selectionRing: Color,           // drivelog_selection_ring — 기본값·선택 항목 테두리
    // 운행 용도 (색만으로 구분하지 않도록 아이콘·글자와 함께 쓴다. 명도 차이: 출퇴근 밝음 > 일반업무 중간 > 비업무 어두움)
    val purposeCommuteBackground: Color,        // drivelog_purpose_commute_background
    val purposeCommuteText: Color,              // drivelog_purpose_commute_text
    val purposeBusinessBackground: Color,       // drivelog_purpose_business_background
    val purposeBusinessText: Color,             // drivelog_purpose_business_text
    val purposePersonalBackground: Color,       // drivelog_purpose_personal_background
    val purposePersonalText: Color,             // drivelog_purpose_personal_text
    val purposeUnclassifiedBackground: Color,   // drivelog_purpose_unclassified_background — 미분류
    val purposeUnclassifiedText: Color,         // drivelog_purpose_unclassified_text
    // 상태
    val statusIntegrity: Color,         // drivelog_status_integrity — 무결성 확인, "차량 기록" 배지
    val statusIntegrityContainer: Color,// drivelog_status_integrity_container
    val statusWarning: Color,           // drivelog_status_warning — 미분류 개수, 오프라인
    val statusWarningContainer: Color,  // drivelog_status_warning_container
    val statusError: Color,             // drivelog_status_error
    val statusErrorContainer: Color,    // drivelog_status_error_container
    val statusInfo: Color,              // drivelog_status_info — 동기화 중
    val scrim: Color,                   // drivelog_scrim — 모달 뒤 어둡게
    val isDark: Boolean
)

// 다크 (차량 기본)
val DriveLogDarkColors = DriveLogColorScheme(
    background = Color(0xFF0A0F16),
    backgroundDriving = Color(0xFF05080C),
    surface = Color(0xFF121923),
    surfaceRaised = Color(0xFF1A2330),
    surfacePressed = Color(0xFF243041),
    border = Color(0xFF2B3747),
    borderStrong = Color(0xFF3E4C5E),
    textPrimary = Color(0xFFF3F5F7),
    textSecondary = Color(0xFFAEB8C5),
    textTertiary = Color(0xFF8693A3),
    textDisabled = Color(0xFF566273),
    buttonPrimaryBackground = Color(0xFF2F6BE0),
    buttonPrimaryBackgroundPressed = Color(0xFF2558BC),
    buttonPrimaryText = Color(0xFFFFFFFF),
    buttonSecondaryBackground = Color(0xFF1A2330),
    buttonSecondaryBackgroundPressed = Color(0xFF243041),
    buttonSecondaryText = Color(0xFFF3F5F7),
    buttonDisabledBackground = Color(0xFF161E29),
    accent = Color(0xFF5CC6E8),
    signature = Color(0xFFE5DACB),
    signatureText = Color(0xFF2A2116),
    selectionRing = Color(0xFFE5DACB),
    purposeCommuteBackground = Color(0xFF5CC6E8),
    purposeCommuteText = Color(0xFF05222D),
    purposeBusinessBackground = Color(0xFF2F6BE0),
    purposeBusinessText = Color(0xFFFFFFFF),
    purposePersonalBackground = Color(0xFF3A4555),
    purposePersonalText = Color(0xFFFFFFFF),
    purposeUnclassifiedBackground = Color(0xFF3A2E14),
    purposeUnclassifiedText = Color(0xFFF2B24C),
    statusIntegrity = Color(0xFF45C98F),
    statusIntegrityContainer = Color(0xFF12291F),
    statusWarning = Color(0xFFF2B24C),
    statusWarningContainer = Color(0xFF2E2410),
    statusError = Color(0xFFFF7A6B),
    statusErrorContainer = Color(0xFF331713),
    statusInfo = Color(0xFF5CC6E8),
    scrim = Color(0xCC05080C),
    isDark = true
)

// 라이트
val DriveLogLightColors = DriveLogColorScheme(
    background = Color(0xFFF4F1EC),
    backgroundDriving = Color(0xFFECE7DF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfacePressed = Color(0xFFE9E4DC),
    border = Color(0xFFDCD5CA),
    borderStrong = Color(0xFFB9B0A3),
    textPrimary = Color(0xFF0E1A2B),
    textSecondary = Color(0xFF4A5668),
    textTertiary = Color(0xFF5E6A7C),
    textDisabled = Color(0xFFA3ABB6),
    buttonPrimaryBackground = Color(0xFF12305E),
    buttonPrimaryBackgroundPressed = Color(0xFF0B2246),
    buttonPrimaryText = Color(0xFFFFFFFF),
    buttonSecondaryBackground = Color(0xFFFFFFFF),
    buttonSecondaryBackgroundPressed = Color(0xFFE9E4DC),
    buttonSecondaryText = Color(0xFF0E1A2B),
    buttonDisabledBackground = Color(0xFFE6E2DB),
    accent = Color(0xFF1C6FB8),
    signature = Color(0xFFD8C9B3),
    signatureText = Color(0xFF2A2116),
    selectionRing = Color(0xFF12305E),
    purposeCommuteBackground = Color(0xFF177A9F),
    purposeCommuteText = Color(0xFFFFFFFF),
    purposeBusinessBackground = Color(0xFF2F6BE0),
    purposeBusinessText = Color(0xFFFFFFFF),
    purposePersonalBackground = Color(0xFF4A5568),
    purposePersonalText = Color(0xFFFFFFFF),
    purposeUnclassifiedBackground = Color(0xFFFBEBCB),
    purposeUnclassifiedText = Color(0xFF7A4A00),
    statusIntegrity = Color(0xFF15704A),
    statusIntegrityContainer = Color(0xFFDDF2E7),
    statusWarning = Color(0xFF8A4F00),
    statusWarningContainer = Color(0xFFFBEBCB),
    statusError = Color(0xFFC0392B),
    statusErrorContainer = Color(0xFFFADCD8),
    statusInfo = Color(0xFF1C6FB8),
    scrim = Color(0x990E1A2B),
    isDark = false
)

val LocalDriveLogColors = staticCompositionLocalOf { DriveLogDarkColors }
