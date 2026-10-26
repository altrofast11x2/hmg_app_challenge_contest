package com.altrostudio.drivelog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.domain.logbook.LogbookSummary
import com.altrostudio.drivelog.ui.components.BigButton
import com.altrostudio.drivelog.ui.components.SummaryCard
import com.altrostudio.drivelog.ui.theme.Dimens
import java.time.YearMonth

/** 정차 중 첫 화면: 기록 상태, 이번 달 요약, 운행기록부·설정으로 가는 버튼 */
@Composable
fun HomeScreen(
    month: YearMonth,
    summary: LogbookSummary?,
    onOpenLogbook: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding)
    ) {
        val compact = maxWidth < Dimens.compactWidth
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_recording_on), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.home_month_title, month.year, month.monthValue), style = MaterialTheme.typography.titleLarge)
            SummaryCard(summary = summary, compact = compact)
            val logbookButton: @Composable (Modifier) -> Unit = {
                BigButton(text = stringResource(R.string.home_open_logbook), onClick = onOpenLogbook, modifier = it)
            }
            val settingsButton: @Composable (Modifier) -> Unit = {
                BigButton(text = stringResource(R.string.home_open_settings), onClick = onOpenSettings, emphasized = false, modifier = it)
            }
            if (compact) {
                logbookButton(Modifier.fillMaxWidth())
                settingsButton(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                    logbookButton(Modifier.weight(1f))
                    settingsButton(Modifier.weight(1f))
                }
            }
        }
    }
}
