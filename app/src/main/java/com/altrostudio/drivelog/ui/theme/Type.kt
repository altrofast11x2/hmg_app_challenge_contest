package com.altrostudio.drivelog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.altrostudio.drivelog.R

// DriveLog 글자 토큰 v0.1 — 원본: design/tokens/Type.kt
// 글꼴: Asta Sans (SIL OFL 1.1, 42dot). Pleos 시스템 UI 글꼴과 같아서 기본 앱과 일관된다. 앱에 넣어 배포 가능
// 설치: design/assets/fonts/asta_sans_variable.ttf → app/src/main/res/font/asta_sans_variable.ttf 로 복사
// 위계: Display(큰 숫자) / Headline / Title / Body / Label  — 최소 Label Small 24sp (글자 높이 18px 이상)
// 크기는 2560×1440·160dpi(1dp=1px) 기준 (가정값). Pleos Figma 자료 확인 후 조정

@OptIn(ExperimentalTextApi::class)
private fun astaSans(weight: Int) = Font(
    resId = R.font.asta_sans_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val AstaSans = FontFamily(astaSans(400), astaSans(500), astaSans(600), astaSans(700), astaSans(800))

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = AstaSans,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
    lineHeightStyle = Trim
)

@Immutable
data class DriveLogTypography(
    val displayLarge: TextStyle = style(144, 152, 700, -0.02),   // drivelog_display_large — 주행 중 거리 숫자
    val display: TextStyle = style(96, 104, 700, -0.02),         // drivelog_display — 요약 숫자 (총 주행거리)
    val headline: TextStyle = style(56, 68, 700, -0.01),         // drivelog_headline — 화면 제목
    val title: TextStyle = style(40, 52, 700),                   // drivelog_title — 섹션 제목, 큰 버튼 글자
    val titleSmall: TextStyle = style(34, 46, 600),              // drivelog_title_small — 목록 항목 제목
    val body: TextStyle = style(30, 44, 500),                    // drivelog_body — 본문
    val bodyStrong: TextStyle = style(30, 44, 700),              // drivelog_body_strong — 본문 강조, 값
    val label: TextStyle = style(28, 36, 600, 0.01),             // drivelog_label — 버튼, 배지, 탭
    val labelSmall: TextStyle = style(24, 32, 600, 0.02)         // drivelog_label_small — 보조 표기 (최소 크기)
)
// 숫자(거리, 시간, 코드)는 표의 열이 맞도록 fontFeatureSettings = "tnum" 을 함께 쓴다
const val DriveLogTabularNumbers = "tnum"

val LocalDriveLogTypography = staticCompositionLocalOf { DriveLogTypography() }
