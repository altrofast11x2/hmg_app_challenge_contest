package com.altrostudio.drivelog.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 선 아이콘 (24 격자, 2.2 획, 둥근 끝). design/mockups/A01-A03.html의 아이콘을 그대로 옮겼다.
 * design/specs/A00-common.md 5절: Material Symbols Rounded로 바꿀 예정 → 바꿀 때 이 파일만 고친다.
 * 색은 Icon(tint = 토큰 색)으로 입힌다 (아래 검은색은 모양만 나타내는 값이다).
 */
object DriveLogIcons {
    val Check by lazy { lineIcon("check", "M5 12.5l4.5 4.5L19 7.5") }
    val Shield by lazy { lineIcon("shield", "M12 3l7.5 2.8v5.7c0 4.6-3.2 8-7.5 9.5-4.3-1.5-7.5-4.9-7.5-9.5V5.8z", "M8.6 12.1l2.4 2.4 4.4-4.6") }
    val List by lazy {
        lineIcon(
            "list",
            "M7.5 3.5h9a2.5 2.5 0 0 1 2.5 2.5v12a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18V6a2.5 2.5 0 0 1 2.5-2.5z",
            "M9 8.5h6M9 12h6M9 15.5h3.5"
        )
    }
    val EyeOff by lazy {
        lineIcon(
            "eye_off",
            "M3 3l18 18",
            "M10.6 5.2A9.8 9.8 0 0 1 12 5c5 0 8.5 4.4 9.5 7-.4 1-1.2 2.4-2.4 3.7M6.3 6.6C4.5 7.9 3.1 9.8 2.5 12c1 2.6 4.5 7 9.5 7 1.7 0 3.2-.5 4.5-1.2",
            "M9.9 10a3 3 0 0 0 4.1 4.1"
        )
    }
    val Target by lazy { lineIcon("target", circle(12f, 12f, 8.5f), circle(12f, 12f, 4f), "M12 12h.01") }
    val Clock by lazy { lineIcon("clock", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2") }
    val Hand by lazy {
        lineIcon(
            "hand",
            "M8 12V6.5a1.5 1.5 0 0 1 3 0V11M11 10.5V5a1.5 1.5 0 0 1 3 0v5.5M14 10.5V6.5a1.5 1.5 0 0 1 3 0V14a6 6 0 0 1-6 6h-.5" +
                "a5.5 5.5 0 0 1-4.7-2.6L4 14.3a1.5 1.5 0 0 1 2.4-1.8L8 14"
        )
    }
    val Gear by lazy {
        lineIcon(
            "gear",
            circle(12f, 12f, 3f),
            "M19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.5-2-3.4-2.3.9a7.5 7.5 0 0 0-2.6-1.5L14 2.5h-4l-.5 2.5a7.5 7.5 0 0 0-2.6 1.5l-2.3-.9-2 3.4 2 1.5" +
                "a7.6 7.6 0 0 0 0 3l-2 1.5 2 3.4 2.3-.9a7.5 7.5 0 0 0 2.6 1.5l.5 2.5h4l.5-2.5a7.5 7.5 0 0 0 2.6-1.5l2.3.9 2-3.4z"
        )
    }
    val Cloud by lazy { lineIcon("cloud", "M7 18.5h10a4 4 0 0 0 .6-8 6 6 0 0 0-11.4 1.6A3.3 3.3 0 0 0 7 18.5z", "M9.5 14l2 2 3.5-3.6") }
    val CloudOff by lazy { lineIcon("cloud_off", "M7 18.5h10a4 4 0 0 0 .6-8 6 6 0 0 0-11.4 1.6A3.3 3.3 0 0 0 7 18.5z", "M4 4l16 16") }
    val Alert by lazy { lineIcon("alert", circle(12f, 12f, 8.5f), "M12 7.8v5M12 16.3h.01") }
    val Back by lazy { lineIcon("back", "M10 6.5L4.5 12l5.5 5.5M5 12h14.5") }
    val Backspace by lazy { lineIcon("backspace", "M8.5 5.5H20v13H8.5L3 12z", "M11.5 9.5l5 5M16.5 9.5l-5 5") }
    val Car by lazy {
        lineIcon(
            "car",
            "M4.5 16.5v-4l1.8-4.6A2 2 0 0 1 8.2 6.5h7.6a2 2 0 0 1 1.9 1.4l1.8 4.6v4",
            "M3.5 16.5h17v2.5h-3v-1.5h-11V19h-3z",
            "M4.5 12.5h15M7.5 14.5h1M15.5 14.5h1"
        )
    }
    val Home by lazy { lineIcon("home", "M4 11l8-6.5 8 6.5", "M6 9.5V19.5h4.5v-5h3v5H18V9.5") }
    val Work by lazy {
        lineIcon(
            "work",
            "M6 7.5h12a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1-2.5 2.5H6A2.5 2.5 0 0 1 3.5 17v-7A2.5 2.5 0 0 1 6 7.5z",
            "M9 7.5V6a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 6v1.5M3.5 12.5h17"
        )
    }
    val Person by lazy { lineIcon("person", circle(12f, 8.5f, 3.8f), "M4.5 20c.8-3.6 3.8-5.6 7.5-5.6s6.7 2 7.5 5.6") }
    val Help by lazy { lineIcon("help", circle(12f, 12f, 8.5f), "M9.6 9.6a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5v.6M12 16.6h.01") }
    val Chevron by lazy { lineIcon("chevron", "M9.5 6l6 6-6 6") }
    val Odometer by lazy { lineIcon("odometer", "M4 16a8 8 0 1 1 16 0", "M12 16l4-5", "M7.5 19.5h9") }
    // 목업에 없는 아이콘: 휴대폰으로 받기 (같은 24 격자·획으로 직접 그림, design/REQUESTS.md에 알림)
    val Phone by lazy { lineIcon("phone", "M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M11 17.5h2") }
    // 목업에 없는 아이콘: 운행기록부 기간 선택 (같은 24 격자·획으로 직접 그림, design/REQUESTS.md에 알림)
    val Calendar by lazy {
        lineIcon("calendar", "M6.5 5.5h11a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z", "M4.5 10h15", "M8.5 3.5v4", "M15.5 3.5v4")
    }

    /** 말로 기록 (마이크) */
    val Mic by lazy {
        lineIcon("mic", "M12 3.5a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0v-5a3 3 0 0 1 3-3z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5v3")
    }

    /** 직접 입력 (연필) */
    val Edit by lazy { lineIcon("edit", "M4.5 19.5h4l10-10-4-4-10 10z", "M13 7l4 4") }

    /** 원(cx, cy, r)을 path 문자열로 */
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun lineIcon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(
                        pathData = PathParser().parsePathString(data).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2.2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round
                    )
                }
            }
            .build()
}
