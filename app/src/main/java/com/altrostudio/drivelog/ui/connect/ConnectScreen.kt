package com.altrostudio.drivelog.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.ButtonSize
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogIcons
import com.altrostudio.drivelog.ui.components.ScreenFrame
import com.altrostudio.drivelog.ui.components.TopBar
import com.altrostudio.drivelog.ui.consent.StepChip
import com.altrostudio.drivelog.ui.theme.DriveLogMotion
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/**
 * A02 차량 연결 (design/specs/A02-connect.md). 정차 중에만 보인다.
 * 시스템 키보드 대신 큰 숫자 키패드로 6자리 코드를 넣고, 6칸이 차면 바로 확인한다.
 * 처음 연결할 때([onSkip]이 있을 때)는 "단계 2 / 2" 칩과 [나중에 연결]을 보여 주고 뒤로 버튼은 숨긴다.
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
    val dimens = DriveLogTheme.dimens
    val keypadEnabled = status !is ConnectStatus.Verifying
    ScreenFrame(modifier = modifier) { portrait ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
            TopBar(title = stringResource(R.string.connect_title), portrait = portrait, onBack = onBack) {
                if (onSkip != null) StepChip(step = 2, total = 2, short = portrait)
            }
            if (portrait) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
                ) {
                    Guide(portrait = true)
                    CodeCells(state, portrait = true)
                    StatusLine(state)
                    Keypad(keypadEnabled, portrait = true, onDigit, onBackspace, onClear, Modifier.fillMaxWidth())
                    VehicleLine(state.vinConfirmed)
                    if (onSkip != null) SkipButton(onSkip)
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingExtraLarge)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(LEFT_COLUMN_WEIGHT)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)
                    ) {
                        Guide(portrait = false)
                        CodeCells(state, portrait = false)
                        StatusLine(state)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(dimens.borderWidth)
                                .background(DriveLogTheme.colors.border)
                        )
                        VehicleLine(state.vinConfirmed)
                        if (onSkip != null) SkipButton(onSkip)
                    }
                    Keypad(keypadEnabled, portrait = false, onDigit, onBackspace, onClear, Modifier.weight(KEYPAD_WEIGHT))
                }
            }
        }
    }
}

// 명세의 왼쪽 860 : 키패드 812 비율
private const val LEFT_COLUMN_WEIGHT = 860f
private const val KEYPAD_WEIGHT = 812f

// 연결 결과 화면의 [시작하기] 버튼 너비 (명세 480 / 앱 창 안쪽 1656)
private const val RESULT_BUTTON_WIDTH_FRACTION = 0.29f

@Composable
private fun Guide(portrait: Boolean) {
    val typography = DriveLogTheme.typography
    val colors = DriveLogTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
        Text(stringResource(R.string.connect_headline), style = if (portrait) typography.title else typography.headline, color = colors.textPrimary)
        Text(stringResource(R.string.connect_description), style = typography.body, color = colors.textSecondary)
        Text(stringResource(R.string.connect_sharing_notice), style = typography.label, color = colors.textTertiary)
    }
}

/** 코드 칸 6개 (3+3 묶음). 칸을 눌러도 키보드가 열리지 않는다 */
@Composable
private fun CodeCells(state: ConnectUiState, portrait: Boolean) {
    val dimens = DriveLogTheme.dimens
    val colors = DriveLogTheme.colors
    val status = state.status
    // 2분할 칸은 104×128 (명세). 가로 칸 토큰에서 한 단계씩 줄인 값
    val cellWidth = if (portrait) dimens.codeCellWidth - dimens.spacingExtraSmall else dimens.codeCellWidth
    val cellHeight = if (portrait) dimens.codeCellHeight - dimens.spacingSmall else dimens.codeCellHeight
    val thickBorder = dimens.borderWidth * 2
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        for (index in 0 until ConnectViewModel.CODE_LENGTH) {
            // 가로: 3번째와 4번째 사이 40 (= 16 + 8 + 16)
            if (index == ConnectViewModel.CODE_LENGTH / 2 && !portrait) Spacer(Modifier.width(dimens.spacingExtraSmall))
            val digit = state.code.getOrNull(index)
            val isCurrent = status == ConnectStatus.Idle && index == state.code.length
            val (borderWidth, borderColor) = when {
                status is ConnectStatus.Error -> thickBorder to colors.statusError
                isCurrent -> thickBorder to colors.accent
                digit != null || status is ConnectStatus.Verifying -> dimens.borderWidth to colors.borderStrong
                else -> dimens.borderWidth to colors.border
            }
            val shape = RoundedCornerShape(dimens.radiusMedium)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(cellWidth, cellHeight)
                    .clip(shape)
                    .background(colors.surfaceRaised)
                    .border(borderWidth, borderColor, shape)
            ) {
                Text(
                    digit?.toString().orEmpty(),
                    style = DriveLogTheme.typography.display.copy(fontFeatureSettings = DriveLogTabularNumbers),
                    color = colors.textPrimary
                )
                if (isCurrent) {
                    // 정적 커서 막대 (깜빡이지 않는다)
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = dimens.spacingMedium)
                            .size(dimens.spacingExtraLarge, dimens.selectionRingWidth)
                            .clip(RoundedCornerShape(dimens.radiusFull))
                            .background(colors.accent)
                    )
                }
            }
        }
    }
}

/** 상태 줄: 문구가 없을 때도 높이(48)를 유지한다 */
@Composable
private fun StatusLine(state: ConnectUiState) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val status = state.status
    val (icon, text, color) = when {
        status is ConnectStatus.Verifying && state.slowVerifying ->
            Triple(DriveLogIcons.Clock, stringResource(R.string.connect_verifying), colors.statusInfo)
        status is ConnectStatus.Error -> Triple(
            DriveLogIcons.Alert,
            stringResource(errorMessage(status.error)),
            if (status.error == ConnectError.OFFLINE) colors.statusWarning else colors.statusError
        )
        else -> Triple<ImageVector?, String, Color>(null, "", colors.textPrimary)
    }
    Row(
        modifier = Modifier.height(dimens.badgeHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSizeSmall))
        Text(text, style = DriveLogTheme.typography.label, color = color)
    }
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
    val dimens = DriveLogTheme.dimens
    val colors = DriveLogTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        Icon(DriveLogIcons.Car, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.iconSizeSmall))
        Text(
            stringResource(if (vinConfirmed) R.string.connect_vehicle_vin else R.string.connect_vehicle_device),
            style = DriveLogTheme.typography.labelSmall,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun SkipButton(onSkip: () -> Unit) {
    DriveLogButton(text = stringResource(R.string.connect_skip), onClick = onSkip, kind = ButtonKind.Text)
}

/** 3×4 숫자 키패드: 1~9, [전체 지움] 0 [한 글자 지움] */
@Composable
private fun Keypad(
    enabled: Boolean,
    portrait: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = DriveLogTheme.dimens
    val gap = if (portrait) dimens.spacingSmall else dimens.spacingMedium
    val keyHeight = if (portrait) dimens.keypadKeyHeightPortrait else dimens.keypadKeyHeightLandscape
    val backspaceDescription = stringResource(R.string.connect_backspace)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (digit in row) {
                    KeypadKey(enabled, keyHeight, Modifier.weight(1f), onClick = { onDigit(digit) }) { color ->
                        KeyDigit(digit.toString(), color)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            KeypadKey(enabled, keyHeight, Modifier.weight(1f), onClick = onClear) { color ->
                Text(
                    stringResource(R.string.connect_clear),
                    style = DriveLogTheme.typography.label,
                    color = if (enabled) DriveLogTheme.colors.textSecondary else color,
                    textAlign = TextAlign.Center
                )
            }
            KeypadKey(enabled, keyHeight, Modifier.weight(1f), onClick = { onDigit('0') }) { color -> KeyDigit("0", color) }
            KeypadKey(
                enabled,
                keyHeight,
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = backspaceDescription },
                onClick = onBackspace
            ) { color ->
                Icon(DriveLogIcons.Backspace, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSizeMedium))
            }
        }
    }
}

@Composable
private fun KeyDigit(text: String, color: Color) {
    Text(text, style = DriveLogTheme.typography.headline.copy(fontFeatureSettings = DriveLogTabularNumbers), color = color)
}

/** 키 하나: 누르면 95%로 줄고 면 색이 바뀐다. 확인 중에는 비활성 */
@Composable
private fun KeypadKey(
    enabled: Boolean,
    height: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit
) {
    val colors = DriveLogTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed && enabled) DriveLogMotion.PRESS_SCALE else 1f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(height)
            .clip(RoundedCornerShape(DriveLogTheme.dimens.radiusMedium))
            .background(if (pressed && enabled) colors.buttonSecondaryBackgroundPressed else colors.buttonSecondaryBackground)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        content(if (enabled) colors.textPrimary else colors.textDisabled)
    }
}

@Composable
private fun ConnectedResult(orgName: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = DriveLogTheme.dimens
    val colors = DriveLogTheme.colors
    ScreenFrame(modifier = modifier) { portrait ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(DriveLogIcons.Check, contentDescription = null, tint = colors.statusIntegrity, modifier = Modifier.size(dimens.iconSizeLarge))
            Text(
                stringResource(R.string.connect_done_title, orgName),
                style = DriveLogTheme.typography.headline,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(stringResource(R.string.connect_done_body), style = DriveLogTheme.typography.body, color = colors.textSecondary)
            DriveLogButton(
                text = stringResource(R.string.connect_done_start),
                onClick = onDone,
                size = ButtonSize.Large,
                modifier = Modifier.fillMaxWidth(if (portrait) 1f else RESULT_BUTTON_WIDTH_FRACTION)
            )
        }
    }
}
