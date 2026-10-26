package com.altrostudio.drivelog.ui.consent

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.ButtonSize
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogCard
import com.altrostudio.drivelog.ui.components.DriveLogChip
import com.altrostudio.drivelog.ui.components.DriveLogIcons
import com.altrostudio.drivelog.ui.components.ScreenFrame
import com.altrostudio.drivelog.ui.components.TopBar
import com.altrostudio.drivelog.ui.settings.PrivacyPolicyScreen
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/**
 * A01 첫 실행 개인정보 동의 (design/specs/A01-consent.md). 정차 중에만 보인다.
 * [동의하고 시작]을 눌렀을 때만 동의로 처리한다. 뒤로가기·빈 곳 터치·앱 전환은 동의가 아니다.
 * [totalSteps]: 서버 연결 단계가 있으면 2 (상단에 "단계 1 / 2"), 없으면 null
 */
@Composable
fun ConsentScreen(onAgree: () -> Unit, onExit: () -> Unit, totalSteps: Int?, modifier: Modifier = Modifier) {
    var declined by rememberSaveable { mutableStateOf(false) }
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    if (showPolicy) {
        PrivacyPolicyScreen(onBack = { showPolicy = false }, modifier = modifier)
        return
    }
    val dimens = DriveLogTheme.dimens
    ScreenFrame(modifier = modifier) { portrait ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
            TopBar(title = stringResource(R.string.consent_screen_title), portrait = portrait) {
                if (totalSteps != null) StepChip(step = 1, total = totalSteps, short = portrait)
            }
            val actions: @Composable ColumnScope.() -> Unit = {
                if (declined) {
                    DeclinedActions(onReturn = { declined = false }, onExit = onExit)
                } else {
                    AgreeActions(onAgree = onAgree, onDecline = { declined = true })
                }
            }
            if (portrait) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
                ) {
                    SummaryLines()
                    ConsentCards(portrait = true, onOpenPolicy = { showPolicy = true })
                }
                // 하단 고정 영역 (스크롤되지 않는다)
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(dimens.borderWidth)
                            .background(DriveLogTheme.colors.border)
                    )
                    Spacer(Modifier.height(dimens.spacingSmall))
                    actions()
                }
            } else {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(dimens.spacingHuge)) {
                    Column(
                        modifier = Modifier
                            .weight(LEFT_COLUMN_WEIGHT)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
                    ) {
                        ConsentCards(portrait = false, onOpenPolicy = { showPolicy = true })
                    }
                    Column(
                        modifier = Modifier
                            .weight(RIGHT_COLUMN_WEIGHT)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)
                    ) {
                        AppMark()
                        SummaryLines()
                        Spacer(Modifier.weight(1f))
                        actions()
                    }
                }
            }
        }
    }
}

// 명세의 왼쪽 1080 : 오른쪽 528 비율
private const val LEFT_COLUMN_WEIGHT = 1080f
private const val RIGHT_COLUMN_WEIGHT = 528f

// 카드 항목 표에서 "항목" 열이 차지하는 비율
private const val ROW_LABEL_WEIGHT = 0.3f

@Composable
fun StepChip(step: Int, total: Int, short: Boolean) {
    DriveLogChip(
        text = stringResource(if (short) R.string.step_chip_short else R.string.step_chip, step, total),
        background = DriveLogTheme.colors.surfaceRaised,
        content = DriveLogTheme.colors.textSecondary
    )
}

@Composable
private fun AppMark() {
    val dimens = DriveLogTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(dimens.buttonHeightMedium)
                .clip(RoundedCornerShape(dimens.radiusMedium))
                .background(DriveLogTheme.colors.buttonPrimaryBackground)
        ) {
            Icon(DriveLogIcons.Odometer, contentDescription = null, tint = DriveLogTheme.colors.signature, modifier = Modifier.size(dimens.iconSizeLarge))
        }
        Text(stringResource(R.string.app_name), style = DriveLogTheme.typography.headline, color = DriveLogTheme.colors.textPrimary)
    }
}

@Composable
private fun SummaryLines() {
    val dimens = DriveLogTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        stringArrayResource(R.array.consent_summary).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall), verticalAlignment = Alignment.CenterVertically) {
                Icon(DriveLogIcons.Check, contentDescription = null, tint = DriveLogTheme.colors.statusIntegrity, modifier = Modifier.size(dimens.iconSizeSmall))
                Text(line, style = DriveLogTheme.typography.body, color = DriveLogTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun ConsentCards(portrait: Boolean, onOpenPolicy: () -> Unit) {
    ConsentCard(DriveLogIcons.List, R.string.consent_card_collected, R.array.consent_collected_labels, R.array.consent_collected_values)
    ConsentCard(DriveLogIcons.EyeOff, R.string.consent_card_not_collected, R.array.consent_not_collected_labels, R.array.consent_not_collected_values)
    ConsentCard(DriveLogIcons.Target, R.string.consent_card_purpose, R.array.consent_purpose_labels, R.array.consent_purpose_values)
    ConsentCard(DriveLogIcons.Clock, R.string.consent_card_retention, R.array.consent_retention_labels, R.array.consent_retention_values)
    DriveLogCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(DriveLogIcons.Hand, R.string.consent_card_refuse)
        Text(stringResource(R.string.consent_refuse), style = DriveLogTheme.typography.body, color = DriveLogTheme.colors.textSecondary)
    }
    DriveLogButton(
        text = stringResource(R.string.consent_open_policy),
        onClick = onOpenPolicy,
        kind = ButtonKind.Text,
        modifier = if (portrait) Modifier.fillMaxWidth() else Modifier
    )
}

/** 동의 카드: 제목 + "항목 — 설명" 2열 표 */
@Composable
private fun ConsentCard(icon: ImageVector, @StringRes title: Int, @ArrayRes labels: Int, @ArrayRes values: Int) {
    val labelTexts = stringArrayResource(labels)
    val valueTexts = stringArrayResource(values)
    DriveLogCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(icon, title)
        Column(verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingSmall)) {
            labelTexts.zip(valueTexts).forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingMedium)) {
                    Text(label, style = DriveLogTheme.typography.bodyStrong, color = DriveLogTheme.colors.textPrimary, modifier = Modifier.weight(ROW_LABEL_WEIGHT))
                    Text(value, style = DriveLogTheme.typography.body, color = DriveLogTheme.colors.textSecondary, modifier = Modifier.weight(1f - ROW_LABEL_WEIGHT))
                }
            }
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, @StringRes title: Int) {
    val dimens = DriveLogTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        Icon(icon, contentDescription = null, tint = DriveLogTheme.colors.accent, modifier = Modifier.size(dimens.iconSizeMedium))
        Text(stringResource(title), style = DriveLogTheme.typography.title, color = DriveLogTheme.colors.textPrimary)
    }
}

@Composable
private fun ColumnScope.AgreeActions(onAgree: () -> Unit, onDecline: () -> Unit) {
    val dimens = DriveLogTheme.dimens
    DriveLogButton(
        text = stringResource(R.string.consent_agree),
        onClick = onAgree,
        size = ButtonSize.Large,
        modifier = Modifier.fillMaxWidth()
    )
    DriveLogButton(
        text = stringResource(R.string.consent_decline),
        onClick = onDecline,
        kind = ButtonKind.Secondary,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(R.string.consent_agree_note),
        style = DriveLogTheme.typography.labelSmall,
        color = DriveLogTheme.colors.textTertiary,
        modifier = Modifier.padding(top = dimens.spacingExtraSmall)
    )
}

/** "동의하지 않음"을 누르면 같은 자리에 확인 영역을 보여 준다 (모달 아님) */
@Composable
private fun ColumnScope.DeclinedActions(onReturn: () -> Unit, onExit: () -> Unit) {
    Text(stringResource(R.string.consent_declined_message), style = DriveLogTheme.typography.bodyStrong, color = DriveLogTheme.colors.textPrimary)
    DriveLogButton(
        text = stringResource(R.string.consent_return),
        onClick = onReturn,
        size = ButtonSize.Large,
        modifier = Modifier.fillMaxWidth()
    )
    DriveLogButton(
        text = stringResource(R.string.consent_exit),
        onClick = onExit,
        kind = ButtonKind.Secondary,
        modifier = Modifier.fillMaxWidth()
    )
}
