package com.altrostudio.drivelog.ui.driving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.ui.components.formatKm
import com.altrostudio.drivelog.ui.components.formatTime
import com.altrostudio.drivelog.ui.theme.Dimens

/**
 * 주행 중 화면. 터치할 요소·애니메이션·자동 스크롤이 없다.
 * 운행을 기록 중이면 출발 시각과 지금까지의 거리만 보여 준다.
 */
@Composable
fun DrivingScreen(drivingTrip: TripEntity?, odometerKm: Float?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (drivingTrip != null) {
            val distance = drivingTrip.startOdometerKm?.let { start -> odometerKm?.let { it - start } }
            Text(stringResource(R.string.driving_recording), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.driving_since, formatTime(drivingTrip.startedAt), formatKm(distance?.coerceAtLeast(0.0))),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(stringResource(R.string.driving_hint), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.driving_locked), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.driving_locked_hint), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
