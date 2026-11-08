package com.altrostudio.drivelog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.distanceKm
import com.altrostudio.drivelog.domain.cabin.ExitCheck
import com.altrostudio.drivelog.domain.cabin.TidyItem
import com.altrostudio.drivelog.domain.logbook.LogbookSummary
import com.altrostudio.drivelog.ui.cabin.DriverCard
import com.altrostudio.drivelog.ui.cabin.DriverCardState
import com.altrostudio.drivelog.ui.cabin.ExitCheckBanner
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogCard
import com.altrostudio.drivelog.ui.components.DriveLogChip
import com.altrostudio.drivelog.ui.components.DriveLogIcons
import com.altrostudio.drivelog.ui.components.IconCircleButton
import com.altrostudio.drivelog.ui.components.IntegrityBadge
import com.altrostudio.drivelog.ui.components.PurposeChip
import com.altrostudio.drivelog.ui.components.ScreenFrame
import com.altrostudio.drivelog.ui.components.TopBar
import com.altrostudio.drivelog.ui.components.formatDateTime
import com.altrostudio.drivelog.ui.components.formatKm
import com.altrostudio.drivelog.ui.components.formatOneDecimal
import com.altrostudio.drivelog.ui.components.purposeColors
import com.altrostudio.drivelog.ui.components.purposeLabel
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme
import java.time.YearMonth

/** 홈 상단의 동기화 칩 상태 (design/specs/A00-common.md 3.4) */
sealed interface SyncChipState {
    /** 서버 설정이 없는 빌드: 이 차량에만 저장 */
    data object LocalOnly : SyncChipState
    data object NotConnected : SyncChipState
    data object Synced : SyncChipState
    data class Pending(val count: Int) : SyncChipState
    data object Offline : SyncChipState
}

/**
 * A03 홈 (design/specs/A03-home.md). 정차 중에만 보인다.
 * 맨 위에 미분류 운행 배너, 하차 전 확인 배너, 공용차 운전자 카드(조직에 연결한 차), 그 아래 이번 달 카드와 최근 운행 카드.
 * 하차 전 확인·운전자 카드는 A03 명세에 없어 공통 컴포넌트와 토큰으로 만들었다 (design/REQUESTS.md 2026-11-03)
 */
@Composable
fun HomeScreen(
    month: YearMonth,
    summary: LogbookSummary?,
    recentTrips: List<TripEntity>,
    unclassifiedTrips: List<TripEntity>,
    sync: SyncChipState,
    onClassifyNow: () -> Unit,
    onOpenLogbook: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCarStatus: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    driverCard: DriverCardState? = null,
    exitCheck: ExitCheck? = null,
    onConfirmDriver: () -> Unit = {},
    onChangeDriver: () -> Unit = {},
    onTidy: (Set<TidyItem>) -> Unit = {},
    onDismissExitCheck: () -> Unit = {}
) {
    val dimens = DriveLogTheme.dimens
    ScreenFrame(modifier = modifier) { portrait ->
        val topBar: @Composable () -> Unit = {
            TopBar(title = stringResource(R.string.app_name), portrait = portrait) {
                SyncChip(sync, iconOnly = portrait)
                IconCircleButton(DriveLogIcons.Help, stringResource(R.string.home_open_help), onOpenHelp)
                IconCircleButton(DriveLogIcons.Car, stringResource(R.string.home_open_car_status), onOpenCarStatus)
                IconCircleButton(DriveLogIcons.Gear, stringResource(R.string.home_open_settings), onOpenSettings)
            }
        }
        val banner: @Composable () -> Unit = {
            if (unclassifiedTrips.isNotEmpty()) UnclassifiedBanner(unclassifiedTrips, portrait, onClassifyNow)
            if (exitCheck != null) ExitCheckBanner(exitCheck, portrait, onTidy, onDismissExitCheck)
            if (driverCard != null) DriverCard(driverCard, portrait, onConfirmDriver, onChangeDriver)
        }
        if (portrait) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
            ) {
                topBar()
                banner()
                MonthCard(month, summary, portrait = true, onOpenLogbook = null, pending = unclassifiedTrips.isNotEmpty(), modifier = Modifier.fillMaxWidth())
                RecentCard(recentTrips.take(PORTRAIT_RECENT_COUNT), onOpenLogbook, Modifier.fillMaxWidth())
                LogbookButton(onOpenLogbook, Modifier.fillMaxWidth())
            }
        } else {
            // 배너(미분류·하차 전 확인·운전자)가 위에 붙어도 카드 높이는 그대로 두고 화면을 스크롤한다
            // (2026-11-04: 배너 두 개가 뜨면 이번 달 카드의 범례가 잘렸다)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cardsHeight = maxHeight - dimens.topBarHeight - dimens.spacingLarge
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)
            ) {
                topBar()
                banner()
                Row(modifier = Modifier.height(cardsHeight), horizontalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
                    MonthCard(
                        month,
                        summary,
                        portrait = false,
                        onOpenLogbook = onOpenLogbook,
                        pending = unclassifiedTrips.isNotEmpty(),
                        modifier = Modifier
                            .weight(MONTH_CARD_WEIGHT)
                            .fillMaxHeight()
                    )
                    RecentCard(
                        recentTrips,
                        onOpenLogbook,
                        Modifier
                            .weight(RECENT_CARD_WEIGHT)
                            .fillMaxHeight()
                    )
                }
            }
            }
        }
    }
}

// 명세의 이번 달 카드 1020 : 최근 운행 카드 644 비율
private const val MONTH_CARD_WEIGHT = 1020f
private const val RECENT_CARD_WEIGHT = 644f
private const val PORTRAIT_RECENT_COUNT = 3

@Composable
private fun SyncChip(sync: SyncChipState, iconOnly: Boolean) {
    val colors = DriveLogTheme.colors
    val (icon, text, color) = when (sync) {
        SyncChipState.LocalOnly -> Triple(DriveLogIcons.CloudOff, stringResource(R.string.sync_chip_local), colors.textSecondary)
        SyncChipState.NotConnected -> Triple(DriveLogIcons.CloudOff, stringResource(R.string.sync_chip_not_connected), colors.textSecondary)
        SyncChipState.Synced -> Triple(DriveLogIcons.Cloud, stringResource(R.string.sync_chip_synced), colors.textSecondary)
        is SyncChipState.Pending -> Triple(DriveLogIcons.Cloud, stringResource(R.string.sync_chip_pending, sync.count), colors.statusInfo)
        SyncChipState.Offline -> Triple(DriveLogIcons.CloudOff, stringResource(R.string.sync_chip_offline), colors.statusWarning)
    }
    DriveLogChip(
        text = if (iconOnly) null else text,
        background = colors.surfaceRaised,
        content = color,
        icon = icon,
        description = text
    )
}

/** 분류 배너: 미분류 운행이 있을 때만. 이 화면의 유일한 샌드 버튼 */
@Composable
private fun UnclassifiedBanner(trips: List<TripEntity>, portrait: Boolean, onClassifyNow: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusLarge)
    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            Icon(DriveLogIcons.Help, contentDescription = null, tint = colors.purposeUnclassifiedText, modifier = Modifier.size(dimens.iconSizeMedium))
            Column {
                Text(
                    stringResource(R.string.home_unclassified_title, trips.size),
                    style = DriveLogTheme.typography.title,
                    color = colors.purposeUnclassifiedText
                )
                if (!portrait) {
                    Text(
                        stringResource(R.string.home_unclassified_oldest, formatDateTime(trips.first().startedAt)),
                        style = DriveLogTheme.typography.body,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
    val button: @Composable (Modifier) -> Unit = { buttonModifier ->
        DriveLogButton(text = stringResource(R.string.home_classify_now), onClick = onClassifyNow, kind = ButtonKind.Signature, modifier = buttonModifier)
    }
    val bannerModifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(colors.purposeUnclassifiedBackground)
        .border(dimens.borderWidth, colors.statusWarning, shape)
        .padding(horizontal = dimens.spacingLarge, vertical = dimens.spacingMedium)
    if (portrait) {
        Column(modifier = bannerModifier, verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            title()
            button(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = bannerModifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { title() }
            button(Modifier)
        }
    }
}

@Composable
private fun MonthCard(
    month: YearMonth,
    summary: LogbookSummary?,
    portrait: Boolean,
    onOpenLogbook: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // 배너에 분류할 운행이 있다 ([나중에]로 넘긴 방금 운행은 아직 요약의 미분류 수에 없다)
    pending: Boolean = false
) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val hasTrips = (summary?.tripCount ?: 0) > 0
    val unclassified = summary?.unclassifiedCount ?: 0
    DriveLogCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            Text(stringResource(R.string.home_month, month.monthValue), style = DriveLogTheme.typography.title, color = colors.textPrimary)
            IntegrityBadge()
            if (hasTrips && unclassified == 0 && !pending) {
                DriveLogChip(
                    text = stringResource(R.string.home_all_classified),
                    background = colors.statusIntegrityContainer,
                    content = colors.statusIntegrity,
                    icon = DriveLogIcons.Check
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingHuge)) {
            Metric(
                label = stringResource(R.string.summary_total),
                value = formatKm(summary?.totalKm ?: 0.0),
                unit = stringResource(R.string.unit_km),
                portrait = portrait
            )
            Metric(
                label = stringResource(if (unclassified > 0) R.string.home_ratio_excluding_unclassified else R.string.summary_ratio),
                value = summary?.workRatioPercent?.let { formatOneDecimal(it) } ?: "–",
                unit = stringResource(R.string.unit_percent),
                portrait = portrait
            )
        }
        if (summary != null && hasTrips) {
            RatioBar(summary)
            Legend(summary)
        } else {
            Text(stringResource(R.string.home_month_empty), style = DriveLogTheme.typography.body, color = colors.textSecondary)
        }
        if (onOpenLogbook != null) {
            Spacer(Modifier.weight(1f))
            LogbookButton(onOpenLogbook)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, portrait: Boolean) {
    val typography = DriveLogTheme.typography
    val colors = DriveLogTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingExtraSmall)) {
        Text(label, style = typography.label, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
            Text(
                value,
                style = (if (portrait) typography.headline else typography.display).copy(fontFeatureSettings = DriveLogTabularNumbers),
                color = colors.textPrimary
            )
            Text(unit, style = if (portrait) typography.titleSmall else typography.title, color = colors.textSecondary)
        }
    }
}

/** 용도별 거리 막대: 출퇴근 · 일반업무 · 비업무 · 미분류 (길이 비례) */
@Composable
private fun RatioBar(summary: LogbookSummary) {
    val dimens = DriveLogTheme.dimens
    val segments = barSegments(summary).filter { it.second > 0.0 }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.spacingMedium)
            .clip(RoundedCornerShape(dimens.radiusFull)),
        horizontalArrangement = Arrangement.spacedBy(dimens.borderWidth)
    ) {
        segments.forEach { (color, km) ->
            Box(
                Modifier
                    .weight(km.toFloat())
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(summary: LogbookSummary) {
    val dimens = DriveLogTheme.dimens
    val colors = DriveLogTheme.colors
    val labels = listOf(
        purposeLabel(TripPurpose.COMMUTE),
        purposeLabel(TripPurpose.BUSINESS),
        purposeLabel(TripPurpose.PERSONAL),
        purposeLabel(TripPurpose.UNCLASSIFIED)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
    ) {
        barSegments(summary).zip(labels).forEach { (segment, label) ->
            val (color, km) = segment
            if (km <= 0.0 && label == labels.last()) return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                Box(
                    Modifier
                        .size(dimens.spacingSmall)
                        .clip(RoundedCornerShape(dimens.radiusSmall / 2))
                        .background(color)
                )
                Text(label, style = DriveLogTheme.typography.label, color = colors.textSecondary)
                Text(
                    formatKm(km),
                    style = DriveLogTheme.typography.bodyStrong.copy(fontFeatureSettings = DriveLogTabularNumbers),
                    color = colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun barSegments(summary: LogbookSummary): List<Pair<Color, Double>> = listOf(
    purposeColors(TripPurpose.COMMUTE).first to summary.commuteKm,
    purposeColors(TripPurpose.BUSINESS).first to summary.businessKm,
    purposeColors(TripPurpose.PERSONAL).first to summary.personalKm,
    DriveLogTheme.colors.borderStrong to summary.unclassifiedKm
)

@Composable
private fun LogbookButton(onOpenLogbook: () -> Unit, modifier: Modifier = Modifier) {
    DriveLogButton(
        text = stringResource(R.string.home_open_logbook),
        onClick = onOpenLogbook,
        kind = ButtonKind.Secondary,
        icon = DriveLogIcons.List,
        modifier = modifier
    )
}

@Composable
private fun RecentCard(trips: List<TripEntity>, onOpenLogbook: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    DriveLogCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_recent_title),
                style = DriveLogTheme.typography.title,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            DriveLogButton(text = stringResource(R.string.home_see_all), onClick = onOpenLogbook, kind = ButtonKind.Text)
        }
        if (trips.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spacingLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
            ) {
                Icon(DriveLogIcons.Car, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.iconSizeLarge))
                Text(stringResource(R.string.home_recent_empty), style = DriveLogTheme.typography.body, color = colors.textSecondary)
            }
        } else {
            trips.forEachIndexed { index, trip ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(dimens.borderWidth)
                            .background(colors.border)
                    )
                }
                RecentTripRow(trip, onClick = onOpenLogbook)
            }
        }
    }
}

/** 최근 운행 한 줄. 운행 상세(A07)가 나오기 전까지는 누르면 운행기록부로 간다 */
@Composable
private fun RecentTripRow(trip: TripEntity, onClick: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val route = if (trip.purpose == TripPurpose.PERSONAL) {
        stringResource(R.string.home_no_address)
    } else {
        val unknown = stringResource(R.string.address_unknown)
        stringResource(R.string.home_route, trip.startAddress ?: unknown, trip.visitNote ?: trip.endAddress ?: unknown)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.listRowHeight)
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(if (pressed) colors.surfacePressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatDateTime(trip.startedAt),
                style = typography.bodyStrong.copy(fontFeatureSettings = DriveLogTabularNumbers),
                color = colors.textPrimary
            )
            Text(route, style = typography.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(dimens.spacingExtraSmall)) {
            Text(
                stringResource(R.string.km_value, formatKm(trip.distanceKm())),
                style = typography.titleSmall.copy(fontFeatureSettings = DriveLogTabularNumbers),
                color = colors.textPrimary
            )
            PurposeChip(trip.purpose)
        }
    }
}
