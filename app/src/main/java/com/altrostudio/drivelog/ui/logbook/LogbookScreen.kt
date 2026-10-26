package com.altrostudio.drivelog.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.domain.logbook.LogbookRow
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
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme
import java.time.format.DateTimeFormatter

/** 월별 운행기록부 (국세청 업무용승용차 운행기록부 열 순서) */
@Composable
fun LogbookScreen(
    state: LogbookUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onClassify: (tripId: String, purpose: TripPurpose) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var classifyingTripId by rememberSaveable { mutableStateOf<String?>(null) }
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
                            BigButton(stringResource(R.string.logbook_prev_month), onPreviousMonth, emphasized = false)
                            Text(
                                stringResource(R.string.home_month_title, state.month.year, state.month.monthValue),
                                style = DriveLogTheme.typography.title,
                                color = DriveLogTheme.colors.textPrimary
                            )
                            BigButton(stringResource(R.string.logbook_next_month), onNextMonth, emphasized = false)
                        }
                        SummaryCard(summary = state.logbook?.summary, compact = compact)
                        IntegrityLine(state.integrity)
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
                if (rows.isEmpty()) {
                    item { Text(stringResource(R.string.logbook_empty), style = DriveLogTheme.typography.body, color = DriveLogTheme.colors.textSecondary) }
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
