package com.altrostudio.drivelog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// DriveLog 테마 v0.1 — 원본: design/tokens/DriveLogTheme.kt
// 사용: DriveLogTheme.colors.textPrimary / DriveLogTheme.typography.title / DriveLogTheme.dimens.spacingLarge
// 차량은 다크가 기본. 시스템 설정이 라이트일 때만 라이트로 바꾼다. 동적 색상(dynamic color)은 쓰지 않는다

object DriveLogTheme {
    val colors: DriveLogColorScheme
        @Composable @ReadOnlyComposable get() = LocalDriveLogColors.current
    val typography: DriveLogTypography
        @Composable @ReadOnlyComposable get() = LocalDriveLogTypography.current
    val dimens: DriveLogDimens
        @Composable @ReadOnlyComposable get() = LocalDriveLogDimens.current
}

@Composable
fun DriveLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DriveLogDarkColors else DriveLogLightColors
    val typography = DriveLogTypography()

    // Material 기본 컴포넌트(스크롤바, 리플 등)도 같은 색을 쓰도록 연결
    val material = if (darkTheme) darkColorScheme(
        primary = colors.buttonPrimaryBackground, onPrimary = colors.buttonPrimaryText,
        secondary = colors.signature, onSecondary = colors.signatureText,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.surface, onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
        outline = colors.borderStrong, outlineVariant = colors.border,
        error = colors.statusError, scrim = colors.scrim
    ) else lightColorScheme(
        primary = colors.buttonPrimaryBackground, onPrimary = colors.buttonPrimaryText,
        secondary = colors.signature, onSecondary = colors.signatureText,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.surface, onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
        outline = colors.borderStrong, outlineVariant = colors.border,
        error = colors.statusError, scrim = colors.scrim
    )

    CompositionLocalProvider(
        LocalDriveLogColors provides colors,
        LocalDriveLogTypography provides typography,
        LocalDriveLogDimens provides DriveLogDimens()
    ) {
        MaterialTheme(colorScheme = material, typography = MaterialAstaSans, content = content)
    }
}

// Material 기본 컴포넌트(날짜 선택 등)의 글자도 Asta Sans로. 크기는 Material 기본값 그대로 둔다
private val MaterialAstaSans: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = AstaSans),
        displayMedium = displayMedium.copy(fontFamily = AstaSans),
        displaySmall = displaySmall.copy(fontFamily = AstaSans),
        headlineLarge = headlineLarge.copy(fontFamily = AstaSans),
        headlineMedium = headlineMedium.copy(fontFamily = AstaSans),
        headlineSmall = headlineSmall.copy(fontFamily = AstaSans),
        titleLarge = titleLarge.copy(fontFamily = AstaSans),
        titleMedium = titleMedium.copy(fontFamily = AstaSans),
        titleSmall = titleSmall.copy(fontFamily = AstaSans),
        bodyLarge = bodyLarge.copy(fontFamily = AstaSans),
        bodyMedium = bodyMedium.copy(fontFamily = AstaSans),
        bodySmall = bodySmall.copy(fontFamily = AstaSans),
        labelLarge = labelLarge.copy(fontFamily = AstaSans),
        labelMedium = labelMedium.copy(fontFamily = AstaSans),
        labelSmall = labelSmall.copy(fontFamily = AstaSans)
    )
}
