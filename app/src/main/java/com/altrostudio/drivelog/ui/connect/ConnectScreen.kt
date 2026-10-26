package com.altrostudio.drivelog.ui.connect

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.BigButton
import com.altrostudio.drivelog.ui.components.ScreenScaffold
import com.altrostudio.drivelog.ui.theme.Dimens

/**
 * 차량 연결 (design/specs/A02-connect.md). 정차 중에만 보인다.
 * 시스템 키보드 대신 큰 숫자 키패드로 6자리 코드를 넣고, 6칸이 차면 바로 확인한다.
 */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onSkip: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val status = state.status
    if (status is ConnectStatus.Connected) {
        ConnectedResult(orgName = status.orgName, onDone = onDone, modifier = modifier)
        return
    }
    ScreenScaffold(title = stringResource(R.string.connect_title), onBack = onBack, modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val compact = maxWidth < Dimens.compactWidth
            val keypadEnabled = status !is ConnectStatus.Verifying
            if (compact) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
                ) {
                    Guide()
                    CodeCells(state)
                    StatusLine(state)
                    Keypad(keypadEnabled, onDigit, onBackspace, onClear, Modifier.fillMaxWidth())
                    VehicleLine(state.vinConfirmed)
                    if (onSkip != null) SkipButton(onSkip)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
                    ) {
                        Guide()
                        CodeCells(state)
                        StatusLine(state)
                        VehicleLine(state.vinConfirmed)
                        if (onSkip != null) SkipButton(onSkip)
                    }
                    Keypad(keypadEnabled, onDigit, onBackspace, onClear, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Guide() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.smallGap)) {
        Text(stringResource(R.string.connect_headline), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.connect_description), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.connect_sharing_notice), style = MaterialTheme.typography.bodyMedium)
    }
}

/** 코드 칸 6개 (3+3 묶음). 칸을 눌러도 키보드가 열리지 않는다 */
@Composable
private fun CodeCells(state: ConnectUiState) {
    val isError = state.status is ConnectStatus.Error
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (index in 0 until ConnectViewModel.CODE_LENGTH) {
            if (index == ConnectViewModel.CODE_LENGTH / 2) Spacer(Modifier.width(Dimens.codeGroupGap))
            else if (index > 0) Spacer(Modifier.width(Dimens.itemGap))
            val digit = state.code.getOrNull(index)
            val isCurrent = state.status == ConnectStatus.Idle && index == state.code.length
            val borderColor = when {
                isError -> MaterialTheme.colorScheme.error
                isCurrent -> MaterialTheme.colorScheme.primary
                digit != null -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Dimens.codeCellWidth, Dimens.codeCellHeight)
                    .border(
                        width = if (isError || isCurrent) Dimens.cellBorderActive else Dimens.cellBorder,
                        color = borderColor,
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                Text(digit?.toString().orEmpty(), style = MaterialTheme.typography.displaySmall)
            }
        }
    }
}

/** 문구가 없을 때도 높이를 유지한다 */
@Composable
private fun StatusLine(state: ConnectUiState) {
    val status = state.status
    val text = when {
        status is ConnectStatus.Verifying && state.slowVerifying -> stringResource(R.string.connect_verifying)
        status is ConnectStatus.Error -> stringResource(errorMessage(status.error))
        else -> ""
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (status is ConnectStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.heightIn(min = Dimens.statusLineHeight)
    )
}

private fun errorMessage(error: ConnectError): Int = when (error) {
    ConnectError.INVALID -> R.string.connect_error_invalid
    ConnectError.EXPIRED -> R.string.connect_error_expired
    ConnectError.OFFLINE -> R.string.connect_error_offline
    ConnectError.TOO_MANY_ATTEMPTS -> R.string.connect_error_too_many
    ConnectError.VEHICLE_IN_OTHER_ORG -> R.string.connect_error_other_org
    ConnectError.NOT_CONFIGURED -> R.string.connect_error_not_configured
    ConnectError.UNKNOWN -> R.string.connect_error_unknown
}

@Composable
private fun VehicleLine(vinConfirmed: Boolean) {
    Text(
        stringResource(if (vinConfirmed) R.string.connect_vehicle_vin else R.string.connect_vehicle_device),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun SkipButton(onSkip: () -> Unit) {
    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
        Text(stringResource(R.string.connect_skip), style = MaterialTheme.typography.titleMedium)
    }
}

/** 3×4 숫자 키패드: 1~9, [전체 지움] 0 [한 글자 지움] */
@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backspaceDescription = stringResource(R.string.connect_backspace)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        for (row in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                for (digit in row) {
                    KeypadKey(digit.toString(), enabled, Modifier.weight(1f)) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            KeypadKey(stringResource(R.string.connect_clear), enabled, Modifier.weight(1f), onClick = onClear)
            KeypadKey("0", enabled, Modifier.weight(1f)) { onDigit('0') }
            KeypadKey(
                "⌫",
                enabled,
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = backspaceDescription },
                onClick = onBackspace
            )
        }
    }
}

@Composable
private fun KeypadKey(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.height(Dimens.keypadKeyHeight)) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ConnectedResult(orgName: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✓", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.connect_done_title, orgName), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.connect_done_body), style = MaterialTheme.typography.bodyLarge)
        BigButton(text = stringResource(R.string.connect_done_start), onClick = onDone)
    }
}
