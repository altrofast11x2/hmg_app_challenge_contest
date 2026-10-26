package com.altrostudio.drivelog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.domain.logbook.LogbookSummary
import com.altrostudio.drivelog.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 제목과 (있으면) 뒤로 버튼이 있는 기본 화면 틀 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            if (onBack != null) {
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
                    Text(stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        content()
    }
}

/** 차량용 큰 버튼. [emphasized]가 false면 테두리 버튼 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = true
) {
    val buttonModifier = modifier.heightIn(min = Dimens.touchTarget)
    if (emphasized) {
        Button(onClick = onClick, modifier = buttonModifier, enabled = enabled) {
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier, enabled = enabled) {
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** 월 요약: 총 주행거리, 업무용 사용거리, 업무사용비율, 미분류 건수 */
@Composable
fun SummaryCard(summary: LogbookSummary?, compact: Boolean, modifier: Modifier = Modifier) {
    val metrics = listOf(
        stringResource(R.string.summary_total) to stringResource(R.string.km_value, formatKm(summary?.totalKm)),
        stringResource(R.string.summary_work) to stringResource(R.string.km_value, formatKm(summary?.workKm)),
        stringResource(R.string.summary_ratio) to (summary?.workRatioPercent?.let { stringResource(R.string.percent_value, formatOneDecimal(it)) } ?: "-"),
        stringResource(R.string.summary_unclassified) to stringResource(R.string.count_value, summary?.unclassifiedCount ?: 0)
    )
    Card(modifier = modifier.fillMaxWidth()) {
        val rows = if (compact) metrics.chunked(2) else listOf(metrics)
        Column(modifier = Modifier.padding(Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
                    row.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            Text(value, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun purposeLabel(purpose: TripPurpose): String = stringResource(
    when (purpose) {
        TripPurpose.UNCLASSIFIED -> R.string.purpose_unclassified
        TripPurpose.COMMUTE -> R.string.purpose_commute
        TripPurpose.BUSINESS -> R.string.purpose_business
        TripPurpose.PERSONAL -> R.string.purpose_personal
    }
)

/** 소수 첫째 자리까지의 km. 값이 없으면 "-" */
fun formatKm(value: Double?): String = value?.let { formatOneDecimal(it) } ?: "-"

fun formatOneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(epochMillis: Long?): String =
    epochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FORMAT) } ?: "-"
