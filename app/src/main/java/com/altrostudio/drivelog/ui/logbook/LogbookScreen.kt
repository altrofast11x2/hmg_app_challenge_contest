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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.altrostudio.drivelog.ui.components.ScreenScaffold
import com.altrostudio.drivelog.ui.components.SummaryCard
import com.altrostudio.drivelog.ui.components.formatKm
import com.altrostudio.drivelog.ui.components.purposeLabel
import com.altrostudio.drivelog.ui.theme.Dimens
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
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val compact = maxWidth < Dimens.compactWidth
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                    BigButton(stringResource(R.string.logbook_prev_month), onPreviousMonth, emphasized = false)
                    Text(
                        stringResource(R.string.home_month_title, state.month.year, state.month.monthValue),
                        style = MaterialTheme.typography.titleLarge
                    )
                    BigButton(stringResource(R.string.logbook_next_month), onNextMonth, emphasized = false)
                }
                SummaryCard(summary = state.logbook?.summary, compact = compact)
                Text(integrityText(state.integrity), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                    BigButton(
                        text = stringResource(if (state.exporting) R.string.logbook_exporting else R.string.logbook_export),
                        onClick = onExport,
                        enabled = !state.exporting && state.logbook?.rows?.isNotEmpty() == true
                    )
                    state.exportResult?.let { Text(exportText(it), style = MaterialTheme.typography.bodyMedium) }
                }
                val rows = state.logbook?.rows.orEmpty()
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.logbook_empty), style = MaterialTheme.typography.bodyLarge)
                } else {
                    if (!compact) TableHeader()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(rows, key = { it.tripId }) { row ->
                            if (compact) CompactRow(row) { classifyingTripId = row.tripId } else TableRow(row) { classifyingTripId = row.tripId }
                            HorizontalDivider()
                        }
                    }
                    Text(stringResource(R.string.logbook_unclassified_note), style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun TableHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderCell(stringResource(R.string.logbook_col_date), Modifier.width(Dimens.logbookDateColumn))
        HeaderCell(stringResource(R.string.logbook_col_driver), Modifier.width(Dimens.logbookDateColumn))
        HeaderCell(stringResource(R.string.logbook_col_start), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.logbook_col_end), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.logbook_col_distance), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.logbook_col_commute), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.logbook_col_business), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.logbook_col_note), Modifier.weight(2f))
        HeaderCell(stringResource(R.string.logbook_col_purpose), Modifier.weight(1.2f))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = modifier.padding(Dimens.smallGap))
}

@Composable
private fun TableRow(row: LogbookRow, onClassify: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
        Cell(row.date.format(DATE_FORMAT), Modifier.width(Dimens.logbookDateColumn))
        Cell(row.driver, Modifier.width(Dimens.logbookDateColumn))
        Cell(formatKm(row.startOdometerKm), Modifier.weight(1f))
        Cell(formatKm(row.endOdometerKm), Modifier.weight(1f))
        Cell(formatKm(row.distanceKm), Modifier.weight(1f))
        Cell(formatKm(row.commuteKm), Modifier.weight(1f))
        Cell(formatKm(row.businessKm), Modifier.weight(1f))
        Cell(row.note ?: "-", Modifier.weight(2f))
        PurposeCell(row, onClassify, Modifier.weight(1.2f))
    }
}

@Composable
private fun CompactRow(row: LogbookRow, onClassify: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
        Column(modifier = Modifier.weight(1f).padding(Dimens.smallGap)) {
            Text("${row.date.format(DATE_FORMAT)} · ${formatKm(row.distanceKm)} km", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatKm(row.startOdometerKm)} → ${formatKm(row.endOdometerKm)} km · ${row.note ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
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
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(Dimens.smallGap)
    )
}

/** 미분류면 [분류] 버튼, 아니면 구분 이름 */
@Composable
private fun PurposeCell(row: LogbookRow, onClassify: () -> Unit, modifier: Modifier = Modifier) {
    if (row.purpose == TripPurpose.UNCLASSIFIED) {
        OutlinedButton(onClick = onClassify, modifier = modifier.heightIn(min = Dimens.touchTarget)) {
            Text(stringResource(R.string.logbook_classify), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Text(purposeLabel(row.purpose), style = MaterialTheme.typography.bodyMedium, modifier = modifier.padding(Dimens.smallGap))
    }
}

@Composable
private fun ClassifyDialog(row: LogbookRow, onSelect: (TripPurpose) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.classify_dialog_title, row.date.format(DATE_FORMAT))) },
        text = { PurposeButtons(suggested = null, onSelect = onSelect, vertical = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
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
