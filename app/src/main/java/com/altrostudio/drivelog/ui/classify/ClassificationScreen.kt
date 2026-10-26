package com.altrostudio.drivelog.ui.classify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.distanceKm
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.ScreenFrame
import com.altrostudio.drivelog.ui.components.TopBar
import com.altrostudio.drivelog.ui.components.formatKm
import com.altrostudio.drivelog.ui.components.formatTime
import com.altrostudio.drivelog.ui.components.purposeColors
import com.altrostudio.drivelog.ui.components.purposeIcon
import com.altrostudio.drivelog.ui.components.purposeLabel
import com.altrostudio.drivelog.ui.theme.DriveLogMotion
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/**
 * 정차 후 원탭 분류. 같은 목적지에서 지난번에 고른 용도를 선택 링으로 강조해 제안한다.
 * [timeoutLabel]: 방금 끝난 운행이면 "N분 뒤 미분류로 저장" 안내에 쓰고, 지난 운행을 분류할 때는 null
 * A05 명세가 나오면 이 화면을 명세대로 바꾼다 (지금은 토큰의 용도 색·아이콘·선택 링만 적용).
 */
@Composable
fun ClassificationScreen(
    trip: TripEntity,
    suggestion: TripPurpose?,
    timeoutLabel: String?,
    onSelect: (TripPurpose) -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = DriveLogTheme.dimens
    val colors = DriveLogTheme.colors
    ScreenFrame(modifier = modifier) { portrait ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)
        ) {
            TopBar(title = stringResource(R.string.classify_title), portrait = portrait)
            TripSummary(trip)
            PurposeButtons(suggestion, onSelect, vertical = portrait, modifier = Modifier.fillMaxWidth())
            if (timeoutLabel != null) {
                Text(stringResource(R.string.classify_timeout_note, timeoutLabel), style = DriveLogTheme.typography.label, color = colors.textTertiary)
            }
            DriveLogButton(
                text = stringResource(R.string.classify_later),
                onClick = onLater,
                kind = ButtonKind.Secondary,
                modifier = if (portrait) Modifier.fillMaxWidth() else Modifier
            )
        }
    }
}

@Composable
private fun TripSummary(trip: TripEntity) {
    val colors = DriveLogTheme.colors
    val typography = DriveLogTheme.typography
    val unknown = stringResource(R.string.address_unknown)
    Column(verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingExtraSmall)) {
        Text(
            stringResource(R.string.classify_summary, formatTime(trip.startedAt), formatTime(trip.endedAt), formatKm(trip.distanceKm())),
            style = typography.title.copy(fontFeatureSettings = DriveLogTabularNumbers),
            color = colors.textPrimary
        )
        Text(
            stringResource(R.string.classify_route, trip.startAddress ?: unknown, trip.endAddress ?: unknown),
            style = typography.body,
            color = colors.textSecondary
        )
    }
}

/** 출퇴근 / 일반업무 / 비업무 버튼 (용도 색 블록 + 아이콘 + 글자). [suggested]는 선택 링으로 강조한다 */
@Composable
fun PurposeButtons(
    suggested: TripPurpose?,
    onSelect: (TripPurpose) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    val dimens = DriveLogTheme.dimens
    val height = if (vertical) dimens.purposeButtonHeightPortrait else dimens.purposeButtonHeightLandscape
    if (vertical) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            for (purpose in PURPOSES) {
                PurposeButton(purpose, purpose == suggested, onSelect, Modifier.fillMaxWidth().height(height))
            }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            for (purpose in PURPOSES) {
                PurposeButton(purpose, purpose == suggested, onSelect, Modifier.weight(1f).height(height))
            }
        }
    }
}

private val PURPOSES = listOf(TripPurpose.COMMUTE, TripPurpose.BUSINESS, TripPurpose.PERSONAL)

@Composable
private fun PurposeButton(purpose: TripPurpose, suggested: Boolean, onSelect: (TripPurpose) -> Unit, modifier: Modifier) {
    val dimens = DriveLogTheme.dimens
    val colors = DriveLogTheme.colors
    val typography = DriveLogTheme.typography
    val (background, content) = purposeColors(purpose)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) DriveLogMotion.PRESS_SCALE else 1f
    val shape = RoundedCornerShape(dimens.radiusLarge)
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (suggested) {
                    Modifier
                        .border(dimens.selectionRingWidth, colors.selectionRing, shape)
                        .padding(dimens.selectionRingOffset)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onSelect(purpose) }
            .padding(dimens.spacingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall, Alignment.CenterVertically)
    ) {
        Icon(purposeIcon(purpose), contentDescription = null, tint = content, modifier = Modifier.size(dimens.iconSizeLarge))
        Text(purposeLabel(purpose), style = typography.title, color = content)
        when {
            suggested -> Text(stringResource(R.string.classify_suggested), style = typography.label, color = content, textAlign = TextAlign.Center)
            purpose == TripPurpose.PERSONAL -> Text(stringResource(R.string.classify_personal_note), style = typography.label, color = content, textAlign = TextAlign.Center)
        }
    }
}
