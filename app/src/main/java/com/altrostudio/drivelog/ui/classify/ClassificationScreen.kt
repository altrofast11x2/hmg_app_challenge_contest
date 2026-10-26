package com.altrostudio.drivelog.ui.classify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.distanceKm
import com.altrostudio.drivelog.ui.components.BigButton
import com.altrostudio.drivelog.ui.components.formatKm
import com.altrostudio.drivelog.ui.components.formatTime
import com.altrostudio.drivelog.ui.components.purposeLabel
import com.altrostudio.drivelog.ui.theme.Dimens

/** 정차 후 원탭 분류. 같은 목적지에서 지난번에 고른 용도를 강조해 제안한다 */
@Composable
fun ClassificationScreen(
    trip: TripEntity,
    suggestion: TripPurpose?,
    timeoutLabel: String,
    onSelect: (TripPurpose) -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding)
    ) {
        val compact = maxWidth < Dimens.compactWidth
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            Text(stringResource(R.string.classify_title), style = MaterialTheme.typography.headlineMedium)
            if (compact) {
                TripSummary(trip)
                PurposeButtons(suggestion, onSelect, vertical = true)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
                    TripSummary(trip, Modifier.weight(1f))
                    PurposeButtons(suggestion, onSelect, vertical = true, modifier = Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.classify_timeout_note, timeoutLabel), style = MaterialTheme.typography.bodyMedium)
            BigButton(text = stringResource(R.string.classify_later), onClick = onLater, emphasized = false)
        }
    }
}

@Composable
private fun TripSummary(trip: TripEntity, modifier: Modifier = Modifier) {
    val unknown = stringResource(R.string.address_unknown)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.smallGap)) {
        Text(
            stringResource(R.string.classify_summary, formatTime(trip.startedAt), formatTime(trip.endedAt), formatKm(trip.distanceKm())),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            stringResource(R.string.classify_route, trip.startAddress ?: unknown, trip.endAddress ?: unknown),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** 출퇴근 / 일반업무 / 비업무 버튼. [suggested]는 채운 버튼으로 강조한다 */
@Composable
fun PurposeButtons(
    suggested: TripPurpose?,
    onSelect: (TripPurpose) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    val purposes = listOf(TripPurpose.COMMUTE, TripPurpose.BUSINESS, TripPurpose.PERSONAL)
    val content: @Composable (TripPurpose, Modifier) -> Unit = { purpose, itemModifier ->
        PurposeButton(purpose, purpose == suggested, onSelect, itemModifier)
    }
    if (vertical) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            purposes.forEach { content(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            purposes.forEach { content(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PurposeButton(purpose: TripPurpose, suggested: Boolean, onSelect: (TripPurpose) -> Unit, modifier: Modifier) {
    val buttonModifier = modifier.heightIn(min = Dimens.purposeButtonHeight)
    val label: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(purposeLabel(purpose), style = MaterialTheme.typography.headlineSmall)
            when {
                suggested -> Text(stringResource(R.string.classify_suggested), style = MaterialTheme.typography.labelLarge)
                purpose == TripPurpose.PERSONAL -> Text(stringResource(R.string.classify_personal_note), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (suggested) {
        Button(onClick = { onSelect(purpose) }, modifier = buttonModifier) { label() }
    } else {
        OutlinedButton(onClick = { onSelect(purpose) }, modifier = buttonModifier) { label() }
    }
}
