package com.altrostudio.drivelog.ui.driving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.ui.components.ScreenFrame
import com.altrostudio.drivelog.ui.components.formatKm
import com.altrostudio.drivelog.ui.components.formatTime
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/**
 * 주행 중 화면. 터치할 요소·애니메이션·자동 스크롤이 없다 (design/specs/A00-common.md 4절).
 * 운행을 기록 중이면 출발 시각과 지금까지의 거리만 보여 준다.
 * [waitingMessage]: 동의 전·연결 전에 출발했을 때의 "시작 전" 변형 문구 ("정차 후 시작할 수 있어요")
 * A04 주행 중 명세가 나오면 이 화면을 명세대로 바꾼다.
 */
@Composable
fun DrivingScreen(
    drivingTrip: TripEntity?,
    odometerKm: Float?,
    waitingMessage: String?,
    modifier: Modifier = Modifier,
    driverName: String? = null
) {
    val colors = DriveLogTheme.colors
    val typography = DriveLogTheme.typography
    ScreenFrame(modifier = modifier, background = colors.backgroundDriving) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingLarge, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                waitingMessage != null -> {
                    Text(waitingMessage, style = typography.headline, color = colors.textPrimary, textAlign = TextAlign.Center)
                }
                drivingTrip != null -> {
                    val distance = drivingTrip.startOdometerKm?.let { start -> odometerKm?.let { it - start } }?.coerceAtLeast(0.0)
                    Text(stringResource(R.string.driving_recording), style = typography.title, color = colors.textSecondary)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
                        Text(
                            formatKm(distance ?: 0.0),
                            style = typography.displayLarge.copy(fontFeatureSettings = DriveLogTabularNumbers),
                            color = colors.textPrimary
                        )
                        Text(stringResource(R.string.unit_km), style = typography.headline, color = colors.textSecondary)
                    }
                    Text(
                        stringResource(R.string.driving_started_at, formatTime(drivingTrip.startedAt)),
                        style = typography.body,
                        color = colors.textSecondary
                    )
                    (drivingTrip.driverName ?: driverName)?.let {
                        Text(stringResource(R.string.driving_driver, it), style = typography.body, color = colors.textSecondary)
                    }
                    Text(stringResource(R.string.driving_hint), style = typography.body, color = colors.textTertiary, textAlign = TextAlign.Center)
                }
                else -> {
                    Text(stringResource(R.string.driving_locked), style = typography.headline, color = colors.textPrimary, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.driving_locked_hint), style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
