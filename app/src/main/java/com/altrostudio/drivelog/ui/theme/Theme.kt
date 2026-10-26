package com.altrostudio.drivelog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Navy80,
    secondary = Green80
)

private val LightColorScheme = lightColorScheme(
    primary = Navy40,
    secondary = Green40
)

// 차량 화면에서 일관된 색을 쓰기 위해 Android 12+ 동적 색상(dynamic color)은 쓰지 않는다
@Composable
fun DriveLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
