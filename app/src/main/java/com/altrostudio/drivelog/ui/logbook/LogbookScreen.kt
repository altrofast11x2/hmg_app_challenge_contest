package com.altrostudio.drivelog.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import com.altrostudio.drivelog.domain.logbook.LogbookRow
import com.altrostudio.drivelog.domain.logbook.MonthChoice
import com.altrostudio.drivelog.ui.classify.PurposeButtons
import com.altrostudio.drivelog.ui.components.BigButton
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogChip
import com.altrostudio.drivelog.ui.components.DriveLogIcons
import com.altrostudio.drivelog.ui.components.PurposeChip
import com.altrostudio.drivelog.ui.components.ScreenScaffold
import com.altrostudio.drivelog.ui.components.SummaryCard
import com.altrostudio.drivelog.ui.components.formatKm
import com.altrostudio.drivelog.ui.components.isPortrait
import com.altrostudio.drivelog.ui.theme.DriveLogMotion
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 운행기록부 (국세청 업무용승용차 운행기록부 열 순서). 기본은 한 달, 달력으로 기간을 고를 수 있다 */
@Composable
fun LogbookScreen(
    state: LogbookUiState,
    onBack: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectPeriod: (LogbookPeriod) -> Unit,
    /** 연결한 조직 이름. 있으면 차에서 CSV를 저장하는 대신 관리자 웹에서 받으라고 안내한다 */
    connectedOrgName: String?,
    onShare: () -> Unit,
    onRetryShare: () -> Unit,
    onDismissShare: () -> Unit,
    onClassify: (tripId: String, purpose: TripPurpose) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var classifyingTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingPeriod by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.logbook_title), onBack = onBack, modifier = modifier) {
        // 요약과 표를 한 목록으로 스크롤한다 (2분할처럼 높이가 낮아도 표가 사라지지 않도록)
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val compact = isPortrait(maxWidth)
            val rows = state.logbook?.rows.orEmpty()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(
                        modifier = Modifier.padding(bottom = DriveLogTheme.dimens.spacingMedium),
                        verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingMedium)
                    ) {
                        // 이전·다음 달 버튼을 여러 번 누르지 않도록 달을 바로 고른다 (사용자 피드백 2026-10-26)
                        MonthPicker(months = state.months, period = state.period, onSelect = onSelectMonth)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)
                        ) {
                            Text(
                                periodTitle(state.period),
                                style = DriveLogTheme.typography.title,
                                color = DriveLogTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            // 달 단위 말고 날짜로도 고른다 (사용자 피드백 2026-10-27)
                            DriveLogButton(
                                text = stringResource(R.string.logbook_pick_period),
                                onClick = { pickingPeriod = true },
                                kind = ButtonKind.Secondary,
                                icon = DriveLogIcons.Calendar
                            )
                        }
                        SummaryCard(summary = state.logbook?.summary, compact = compact)
                        IntegrityLine(state.integrity)
                        // 조직에 연결한 차는 기록이 관리자 웹에 모이므로, 차 안에 CSV 파일을 남기지 않고 웹에서 받게 한다
                        // (사용자 피드백 2026-10-27: 차에서 파일로 저장해도 쓸 데가 없다). 연결하지 않은 차는 CSV로 저장한다
                        if (connectedOrgName != null) {
                            // 휴대폰으로 받기: 차에는 메일·메신저 앱이 없어서 QR 코드로 휴대폰에 넘긴다 (사용자 요청 2026-10-27)
                            val shareButton = @Composable {
                                DriveLogButton(
                                    text = stringResource(R.string.logbook_share_button),
                                    onClick = onShare,
                                    kind = ButtonKind.Secondary,
                                    icon = DriveLogIcons.Phone
                                )
                            }
                            if (compact) {
                                Column(verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
                                    ExportOnWebNote(connectedOrgName)
                                    shareButton()
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
                                    shareButton()
                                    ExportOnWebNote(connectedOrgName)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
                                BigButton(
                                    text = stringResource(if (state.exporting) R.string.logbook_exporting else R.string.logbook_export),
                                    onClick = onExport,
                                    enabled = !state.exporting && rows.isNotEmpty()
                                )
                                state.exportResult?.let { Text(exportText(it), style = DriveLogTheme.typography.label, color = DriveLogTheme.colors.textSecondary) }
                            }
                        }
                    }
                }
                if (rows.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (state.period.wholeMonth != null) R.string.logbook_empty else R.string.logbook_empty_period),
                            style = DriveLogTheme.typography.body,
                            color = DriveLogTheme.colors.textSecondary
                        )
                    }
                } else {
                    if (!compact) item { TableHeader() }
                    items(rows, key = { it.tripId }) { row ->
                        if (compact) CompactRow(row) { classifyingTripId = row.tripId } else TableRow(row) { classifyingTripId = row.tripId }
                        HorizontalDivider(thickness = DriveLogTheme.dimens.borderWidth, color = DriveLogTheme.colors.border)
                    }
                    item {
                        Text(
                            stringResource(R.string.logbook_unclassified_note),
                            style = DriveLogTheme.typography.label,
                            color = DriveLogTheme.colors.textTertiary,
                            modifier = Modifier.padding(top = DriveLogTheme.dimens.spacingMedium)
                        )
                    }
                }
            }
        }
    }
    state.share?.let { share ->
        ShareDialog(share = share, periodTitle = periodTitle(state.period), onRetry = onRetryShare, onDismiss = onDismissShare)
    }
    if (pickingPeriod) {
        PeriodDialog(
            period = state.period,
            onApply = { period ->
                onSelectPeriod(period)
                pickingPeriod = false
            },
            onDismiss = { pickingPeriod = false }
        )
    }
    val tripId = classifyingTripId
    val row = state.logbook?.rows?.firstOrNull { it.tripId == tripId }
    if (tripId != null && row != null) {
        ClassifyDialog(
            row = row,
            onSelect = { purpose ->
                onClassify(tripId, purpose)
                classifyingTripId = null
            },
            onDismiss = { classifyingTripId = null }
        )
    }
}

/**
 * 월 목록: 가로로 넘기며 달을 바로 고른다. 운행이 있는 달은 건수를 보여 준다.
 * 한 달 전체를 보고 있을 때만 그 달을 강조하고, 달력으로 고른 기간이면 시작 날짜가 든 달이 보이게만 한다.
 */
@Composable
private fun MonthPicker(months: List<MonthChoice>, period: LogbookPeriod, onSelect: (YearMonth) -> Unit) {
    val dimens = DriveLogTheme.dimens
    val selected = period.wholeMonth
    val shownMonth = selected ?: YearMonth.from(period.start)
    val selectedIndex = months.indexOfFirst { it.month == shownMonth }
    val listState = rememberLazyListState()
    // 고른 달이 보이도록 (처음 열 때와 목록이 바뀔 때)
    LaunchedEffect(months.size, selectedIndex) {
        if (selectedIndex >= 0) listState.scrollToItem((selectedIndex - MONTHS_BEFORE_SELECTED).coerceAtLeast(0))
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        itemsIndexed(months, key = { _, choice -> choice.month.toString() }) { index, choice ->
            MonthChip(
                choice = choice,
                selected = choice.month == selected,
                showYear = index == 0 || choice.month.monthValue == 1,
                onClick = { onSelect(choice.month) }
            )
        }
    }
}

// 고른 달 앞에 보여 줄 달 수
private const val MONTHS_BEFORE_SELECTED = 3

@Composable
private fun MonthChip(choice: MonthChoice, selected: Boolean, showYear: Boolean, onClick: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) DriveLogMotion.PRESS_SCALE else 1f
    val shape = RoundedCornerShape(dimens.radiusMedium)
    val background = when {
        selected -> colors.buttonPrimaryBackground
        pressed -> colors.buttonSecondaryBackgroundPressed
        else -> colors.buttonSecondaryBackground
    }
    val main = when {
        selected -> colors.buttonPrimaryText
        choice.tripCount == 0 -> colors.textTertiary
        else -> colors.textPrimary
    }
    val sub = if (selected) colors.buttonPrimaryText else colors.textTertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = dimens.touchTargetMinimum)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier else Modifier.border(dimens.borderWidth, colors.border, shape))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spacingMedium, vertical = dimens.spacingExtraSmall)
    ) {
        Text(
            if (showYear) stringResource(R.string.logbook_month_chip_year, choice.month.year) else " ",
            style = typography.labelSmall,
            color = sub
        )
        Text(stringResource(R.string.home_month, choice.month.monthValue), style = typography.titleSmall, color = main)
        Text(
            if (choice.tripCount > 0) stringResource(R.string.count_value, choice.tripCount) else "–",
            style = typography.labelSmall,
            color = sub
        )
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("MM.dd (E)")

// 표의 열 비율: 날짜, 사용자, 주행 전, 주행 후, 주행거리, 출퇴근용, 일반업무용, 비고, 구분
private val COLUMN_WEIGHTS = listOf(1.3f, 1.3f, 1f, 1f, 1f, 1f, 1f, 2f, 1.4f)

@Composable
private fun TableHeader() {
    val headers = listOf(
        R.string.logbook_col_date, R.string.logbook_col_driver, R.string.logbook_col_start, R.string.logbook_col_end,
        R.string.logbook_col_distance, R.string.logbook_col_commute, R.string.logbook_col_business, R.string.logbook_col_note,
        R.string.logbook_col_purpose
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        headers.zip(COLUMN_WEIGHTS).forEach { (header, weight) ->
            Text(
                stringResource(header),
                style = DriveLogTheme.typography.labelSmall,
                color = DriveLogTheme.colors.textSecondary,
                modifier = Modifier
                    .weight(weight)
                    .padding(DriveLogTheme.dimens.spacingExtraSmall)
            )
        }
    }
}

@Composable
private fun TableRow(row: LogbookRow, onClassify: () -> Unit) {
    val cells = listOf(
        row.date.format(DATE_FORMAT), row.driver, formatKm(row.startOdometerKm), formatKm(row.endOdometerKm),
        formatKm(row.distanceKm), formatKm(row.commuteKm), formatKm(row.businessKm), row.note ?: "-"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = DriveLogTheme.dimens.listRowHeight)) {
        cells.zip(COLUMN_WEIGHTS).forEach { (text, weight) -> Cell(text, Modifier.weight(weight)) }
        PurposeCell(row, onClassify, Modifier.weight(COLUMN_WEIGHTS.last()))
    }
}

@Composable
private fun CompactRow(row: LogbookRow, onClassify: () -> Unit) {
    val dimens = DriveLogTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = dimens.listRowHeight)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = dimens.spacingExtraSmall)
        ) {
            Text(
                "${row.date.format(DATE_FORMAT)} · ${formatKm(row.distanceKm)} km",
                style = DriveLogTheme.typography.bodyStrong.copy(fontFeatureSettings = DriveLogTabularNumbers),
                color = DriveLogTheme.colors.textPrimary
            )
            Text(
                "${formatKm(row.startOdometerKm)} → ${formatKm(row.endOdometerKm)} km · ${row.note ?: "-"}",
                style = DriveLogTheme.typography.labelSmall,
                color = DriveLogTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PurposeCell(row, onClassify)
    }
}

@Composable
private fun RowScope.Cell(text: String, modifier: Modifier) {
    Text(
        text,
        style = DriveLogTheme.typography.label.copy(fontFeatureSettings = DriveLogTabularNumbers),
        color = DriveLogTheme.colors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(DriveLogTheme.dimens.spacingExtraSmall)
    )
}

/** 미분류면 [분류] 버튼, 아니면 용도 칩 */
@Composable
private fun PurposeCell(row: LogbookRow, onClassify: () -> Unit, modifier: Modifier = Modifier) {
    if (row.purpose == TripPurpose.UNCLASSIFIED) {
        DriveLogButton(text = stringResource(R.string.logbook_classify), onClick = onClassify, kind = ButtonKind.Secondary, modifier = modifier)
    } else {
        PurposeChip(row.purpose, modifier = modifier.padding(DriveLogTheme.dimens.spacingExtraSmall))
    }
}

@Composable
private fun ClassifyDialog(row: LogbookRow, onSelect: (TripPurpose) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DriveLogTheme.colors.surfaceRaised,
        title = {
            Text(
                stringResource(R.string.classify_dialog_title, row.date.format(DATE_FORMAT)),
                style = DriveLogTheme.typography.title,
                color = DriveLogTheme.colors.textPrimary
            )
        },
        text = { PurposeButtons(suggested = null, onSelect = onSelect, vertical = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {},
        dismissButton = { DriveLogButton(text = stringResource(R.string.cancel), onClick = onDismiss, kind = ButtonKind.Text) }
    )
}

/** 조직에 연결한 차: 운행기록부와 세무서 제출용 서식은 관리자 웹에서 받는다 (직원은 직접 가입) */
@Composable
private fun ExportOnWebNote(orgName: String) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceRaised)
            .padding(horizontal = dimens.spacingMedium, vertical = dimens.spacingSmall)
    ) {
        Icon(DriveLogIcons.Cloud, contentDescription = null, tint = colors.accent, modifier = Modifier.size(dimens.iconSizeSmall))
        // 직원은 관리자 웹에 직접 가입해 받는다 (사용자 요청 2026-10-27: 관리자가 직원마다 파일을 건넬 수 없다)
        val webHost = stringResource(R.string.web_url).substringAfter("://").trimEnd('/')
        Text(stringResource(R.string.logbook_export_on_web, orgName, webHost), style = DriveLogTheme.typography.label, color = colors.textSecondary)
    }
}

/** 해시 체인 확인 결과 한 줄 */
@Composable
private fun IntegrityLine(integrity: Integrity) {
    val colors = DriveLogTheme.colors
    when (integrity) {
        is Integrity.Valid -> DriveLogChip(
            text = integrityText(integrity),
            background = colors.statusIntegrityContainer,
            content = colors.statusIntegrity,
            icon = DriveLogIcons.Shield
        )
        is Integrity.Broken -> DriveLogChip(
            text = integrityText(integrity),
            background = colors.statusErrorContainer,
            content = colors.statusError,
            icon = DriveLogIcons.Alert
        )
        Integrity.Checking -> Text(integrityText(integrity), style = DriveLogTheme.typography.label, color = colors.textSecondary)
    }
}

@Composable
private fun integrityText(integrity: Integrity): String = when (integrity) {
    Integrity.Checking -> stringResource(R.string.integrity_checking)
    is Integrity.Valid -> stringResource(R.string.integrity_valid, integrity.count)
    is Integrity.Broken -> stringResource(R.string.integrity_broken, integrity.atSeq, integrity.reason)
}

@Composable
private fun exportText(result: ExportResult): String = when (result) {
    is ExportResult.Saved -> stringResource(R.string.logbook_export_done, result.path)
    is ExportResult.Failed -> stringResource(R.string.logbook_export_failed, result.message)
}

/** 한 달 전체면 "2026년 10월", 하루면 "2026.10.26", 아니면 "2026.10.03 – 2026.10.17" */
@Composable
private fun periodTitle(period: LogbookPeriod): String {
    period.wholeMonth?.let { return stringResource(R.string.home_month_title, it.year, it.monthValue) }
    val start = period.start.format(PERIOD_DATE_FORMAT)
    return if (period.start == period.end) start else stringResource(R.string.logbook_period_range, start, period.end.format(PERIOD_DATE_FORMAT))
}

private val PERIOD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd")

// Material 달력의 날짜 칸 크기와 너비 (material3 DatePickerModalTokens). 차량 화면에서는 너무 작아서
// 날짜 칸이 앱의 최소 터치 크기(drivelog_touch_target_minimum)가 되도록 달력 전체를 키운다
private val MATERIAL_DATE_CELL = 40.dp
private val MATERIAL_PICKER_WIDTH = 360.dp
// 머리글 없는 Material 달력의 높이 (달·연도 줄 + 요일 줄 + 6주, 에뮬레이터 화면에서 잰 대략값)
private val MATERIAL_CALENDAR_HEIGHT = 408.dp

// 달력에서 고를 수 있는 해 (월 칩과 같이 최대 2년 전까지)
private const val PICKER_YEARS_BACK = 2

// 날짜 칸 두 개를 한 줄에 둘 수 있는 최소 너비. 더 좁으면 날짜가 두 줄로 깨져서 위아래로 쌓는다
private val DATE_FIELDS_ROW_MIN_WIDTH = 520.dp

/**
 * 달력으로 기간 고르기 (사용자 요청 2026-10-27: 웹의 날짜 입력처럼 한 달씩 보고 연도·날짜를 고르게).
 * 위의 [시작 날짜]·[끝 날짜] 칸을 고르고 달력에서 날짜를 누른다. 시작을 고르면 끝 날짜 칸으로 넘어간다.
 * 달력은 Material 3 DatePicker: "2026년 10월 ▾"를 누르면 연도, ◀ ▶로 달을 바꾼다.
 * 오늘 뒤는 고를 수 없고, 366일을 넘으면 적용할 수 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDialog(period: LogbookPeriod, onApply: (LogbookPeriod) -> Unit, onDismiss: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val today = remember { LocalDate.now() }
    val todayMillis = today.toUtcMillis()
    val selectableDates = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        }
    }
    var startDate by rememberSaveable { mutableStateOf(period.start) }
    var endDate by rememberSaveable { mutableStateOf(period.end) }
    var editingEnd by rememberSaveable { mutableStateOf(false) }
    // Material 달력은 날짜를 UTC 0시 밀리초로 주고받는다
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = period.start.toUtcMillis(),
        yearRange = (today.year - PICKER_YEARS_BACK)..today.year,
        selectableDates = selectableDates
    )
    // Material 달력은 바깥에서 displayedMonthMillis를 바꾸면 달 이름만 바뀌고 날짜 칸은 그대로라서,
    // 달을 옮길 때는 달력을 새로 그린다 (material3 1.3.0)
    var calendarKey by remember { mutableIntStateOf(0) }
    val showMonth: (Long) -> Unit = { millis ->
        pickerState.displayedMonthMillis = millis
        calendarKey++
    }
    // 고르는 칸을 바꾼다. showItsMonth면 달력도 그 날짜가 든 달로 넘긴다
    val edit: (Boolean, Boolean) -> Unit = { end, showItsMonth ->
        editingEnd = end
        val date = (if (end) endDate else startDate).toUtcMillis()
        pickerState.selectedDateMillis = date
        if (showItsMonth) showMonth(date)
    }
    // 달력에서 누른 날짜를 지금 고르는 칸에 넣는다. 시작이 끝보다 늦어지면 끝도 같은 날로 맞춘다
    LaunchedEffect(pickerState.selectedDateMillis) {
        val picked = pickerState.selectedDateMillis?.toUtcDate() ?: return@LaunchedEffect
        if (!editingEnd && picked != startDate) {
            startDate = picked
            if (endDate < picked) endDate = picked
            edit(true, false)
        } else if (editingEnd && picked != endDate) {
            endDate = picked
            if (picked < startDate) startDate = picked
        }
    }
    val picked = LogbookPeriod.pick(startDate, endDate)
    val density = LocalDensity.current
    // 대화상자에서 달력이 아닌 부분의 높이: 제목, 날짜 칸, 안내, 버튼, 간격 4개, 안쪽·바깥 여백
    val chromeHeight = with(density) {
        typography.title.lineHeight.toDp() + typography.labelSmall.lineHeight.toDp() +
            typography.titleSmall.lineHeight.toDp() + typography.label.lineHeight.toDp()
    } + dimens.spacingExtraSmall * 2 + dimens.buttonHeightMedium + dimens.spacingSmall * 4 +
        dimens.spacingMedium * 2 + dimens.spacingLarge * 2
    val chromeWidth = dimens.spacingMedium * 2 + dimens.spacingLarge * 2

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints {
            // 날짜 칸이 최소 터치 크기가 되게 키우되, 좁거나 낮은 창(2분할)에서는 들어가는 만큼만 키운다
            val scale = minOf(
                dimens.touchTargetMinimum / MATERIAL_DATE_CELL,
                (maxWidth - chromeWidth) / MATERIAL_PICKER_WIDTH,
                (maxHeight - chromeHeight) / MATERIAL_CALENDAR_HEIGHT
            ).coerceAtLeast(1f)
            val contentWidth = minOf(maxWidth - dimens.spacingLarge * 2, MATERIAL_PICKER_WIDTH * scale + dimens.spacingMedium * 2) -
                dimens.spacingMedium * 2
            val dateFields: @Composable (Modifier) -> Unit = { fieldModifier ->
                DateField(
                    label = stringResource(R.string.logbook_period_start),
                    date = startDate,
                    active = !editingEnd,
                    onClick = { edit(false, true) },
                    modifier = fieldModifier
                )
                DateField(
                    label = stringResource(R.string.logbook_period_end),
                    date = endDate,
                    active = editingEnd,
                    onClick = { edit(true, true) },
                    modifier = fieldModifier
                )
            }
            Column(
                modifier = Modifier
                    .padding(dimens.spacingLarge)
                    .widthIn(max = MATERIAL_PICKER_WIDTH * scale + dimens.spacingMedium * 2)
                    .clip(RoundedCornerShape(dimens.radiusLarge))
                    .background(colors.surfaceRaised)
                    // 그래도 높이가 모자라면 대화상자 안을 스크롤한다
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
            ) {
                Text(stringResource(R.string.logbook_period_title), style = typography.title, color = colors.textPrimary)
                if (contentWidth < DATE_FIELDS_ROW_MIN_WIDTH) {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) { dateFields(Modifier.fillMaxWidth()) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) { dateFields(Modifier.weight(1f)) }
                }
                Text(
                    if (picked == null) {
                        stringResource(R.string.logbook_period_too_long, LogbookPeriod.MAX_DAYS)
                    } else {
                        stringResource(R.string.logbook_period_hint)
                    },
                    style = typography.label,
                    color = if (picked == null) colors.statusError else colors.textSecondary
                )
                CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                    key(calendarKey) {
                        DatePicker(
                            state = pickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = colors.surfaceRaised,
                                weekdayContentColor = colors.textSecondary,
                                navigationContentColor = colors.textPrimary,
                                yearContentColor = colors.textPrimary,
                                disabledYearContentColor = colors.textDisabled,
                                currentYearContentColor = colors.accent,
                                selectedYearContentColor = colors.buttonPrimaryText,
                                selectedYearContainerColor = colors.buttonPrimaryBackground,
                                dayContentColor = colors.textPrimary,
                                disabledDayContentColor = colors.textDisabled,
                                selectedDayContentColor = colors.buttonPrimaryText,
                                selectedDayContainerColor = colors.buttonPrimaryBackground,
                                todayContentColor = colors.accent,
                                todayDateBorderColor = colors.accent,
                                dividerColor = colors.border
                            )
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    // 웹 날짜 입력의 ● 버튼처럼 이번 달로 돌아간다
                    DriveLogButton(
                        text = stringResource(R.string.logbook_period_this_month),
                        onClick = { showMonth(todayMillis) },
                        kind = ButtonKind.Text
                    )
                    Box(modifier = Modifier.weight(1f))
                    DriveLogButton(text = stringResource(R.string.cancel), onClick = onDismiss, kind = ButtonKind.Text)
                    DriveLogButton(
                        text = stringResource(R.string.logbook_period_apply),
                        onClick = { picked?.let(onApply) },
                        enabled = picked != null,
                        modifier = Modifier.padding(start = dimens.spacingSmall)
                    )
                }
            }
        }
    }
}

/** 기간 대화상자의 [시작 날짜]·[끝 날짜] 칸. 지금 고르는 칸은 테두리로 표시한다 */
@Composable
private fun DateField(label: String, date: LocalDate, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)
    Column(
        modifier = modifier
            .heightIn(min = dimens.touchTargetMinimum)
            .clip(shape)
            .background(colors.buttonSecondaryBackground)
            .border(if (active) dimens.borderWidth * 2 else dimens.borderWidth, if (active) colors.selectionRing else colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spacingMedium, vertical = dimens.spacingExtraSmall)
    ) {
        Text(label, style = DriveLogTheme.typography.labelSmall, color = if (active) colors.accent else colors.textSecondary)
        Text(
            date.format(PERIOD_DATE_FORMAT),
            style = DriveLogTheme.typography.titleSmall.copy(fontFeatureSettings = DriveLogTabularNumbers),
            color = colors.textPrimary
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
