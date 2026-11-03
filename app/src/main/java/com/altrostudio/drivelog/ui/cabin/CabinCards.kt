package com.altrostudio.drivelog.ui.cabin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.vehicle.Comfort
import com.altrostudio.drivelog.domain.cabin.CabinPhrases
import com.altrostudio.drivelog.domain.cabin.ExitCheck
import com.altrostudio.drivelog.domain.cabin.TidyItem
import com.altrostudio.drivelog.domain.driver.DriverProfile
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogChip
import com.altrostudio.drivelog.ui.components.DriveLogIcons
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/** 홈 운전자 카드에 필요한 값 */
data class DriverCardState(
    val current: DriverProfile?,
    val roster: List<DriverProfile>,
    /** 오래 확인하지 않아 "오늘 운전하시는 분이 맞나요?"로 묻는다 */
    val needsConfirmation: Boolean,
    val applying: Boolean = false,
    /** 방금 고른 결과 문장 ("김민준 님 설정으로 맞췄어요") */
    val message: String? = null
)

/**
 * 공용차 운전자 카드 (홈, 정차 중). 지금 운전자와 그분의 맞춤 설정을 보여 주고 [바꾸기]로 고른다.
 * 오래 확인하지 않았으면 샌드 테두리로 "오늘 운전하시는 분이 맞나요?"를 묻는다 ([맞아요]도 맞춤 설정을 다시 적용한다)
 */
@Composable
fun DriverCard(
    state: DriverCardState,
    portrait: Boolean,
    onConfirm: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val shape = RoundedCornerShape(dimens.radiusLarge)
    val asking = state.needsConfirmation
    val current = state.current
    val title = when {
        current == null -> stringResource(R.string.driver_card_none)
        asking -> stringResource(R.string.driver_card_confirm_title)
        else -> stringResource(R.string.driver_card_title)
    }
    val info: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            Icon(DriveLogIcons.Person, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(dimens.iconSizeMedium))
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingExtraSmall)) {
                Text(title, style = typography.label, color = if (asking) colors.signature else colors.textSecondary)
                if (current != null) {
                    Text(current.label, style = typography.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val line = state.message ?: if (asking || current == null) stringResource(R.string.driver_card_hint) else null
                if (line != null) Text(line, style = typography.body, color = colors.textSecondary)
                if (current != null && state.message == null && !asking) ComfortChips(current.comfort)
            }
        }
    }
    val buttons: @Composable (Modifier) -> Unit = { buttonModifier ->
        Row(modifier = buttonModifier, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            if (asking && current != null) {
                DriveLogButton(
                    text = stringResource(R.string.driver_confirm_yes),
                    onClick = onConfirm,
                    loading = state.applying,
                    modifier = if (portrait) Modifier.weight(1f) else Modifier
                )
            }
            DriveLogButton(
                text = stringResource(if (current == null) R.string.driver_choose else R.string.driver_change),
                onClick = onChange,
                kind = if (current == null) ButtonKind.Primary else ButtonKind.Secondary,
                loading = state.applying && !asking,
                modifier = if (portrait) Modifier.weight(1f) else Modifier
            )
        }
    }
    val cardModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(colors.surface)
        .then(if (asking || current == null) Modifier.border(dimens.borderWidth, colors.signature, shape) else Modifier)
        .padding(horizontal = dimens.spacingLarge, vertical = dimens.spacingMedium)
    if (portrait) {
        Column(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            info()
            buttons(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = cardModifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { info() }
            buttons(Modifier)
        }
    }
}

/** 맞춤 설정 요약: 온도 · 열선/통풍 · 무드등 색 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComfortChips(comfort: Comfort, modifier: Modifier = Modifier) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    if (comfort.isEmpty) {
        Text(stringResource(R.string.driver_comfort_none), style = DriveLogTheme.typography.body, color = colors.textTertiary, modifier = modifier)
        return
    }
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall), verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        comfort.tempC?.let {
            DriveLogChip(text = stringResource(R.string.driver_comfort_temp, CabinPhrases.temperature(it)), background = colors.surfaceRaised, content = colors.textPrimary)
        }
        comfort.seatHeat?.let { level ->
            val text = when {
                level > 0 -> stringResource(R.string.driver_comfort_heat, level)
                level < 0 -> stringResource(R.string.driver_comfort_vent, -level)
                else -> stringResource(R.string.driver_comfort_seat_off)
            }
            DriveLogChip(text = text, background = colors.surfaceRaised, content = colors.textPrimary)
        }
        comfort.ambientColor?.let { rgb ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radiusFull))
                    .background(colors.surfaceRaised)
                    .padding(horizontal = dimens.spacingMedium, vertical = dimens.spacingExtraSmall)
            ) {
                // 운전자가 차에서 고른 무드등 색 그대로 (화면 색 토큰이 아니라 차량 데이터)
                Box(
                    Modifier
                        .size(dimens.iconSizeSmall)
                        .clip(CircleShape)
                        .background(Color(OPAQUE or rgb))
                        .border(dimens.borderWidth, colors.borderStrong, CircleShape)
                )
                Text(stringResource(R.string.driver_comfort_ambient), style = DriveLogTheme.typography.label, color = colors.textPrimary)
            }
        }
    }
}

/** 운전자 고르기. 이 차에 배정된 사람, 최근에 운전한 사람이 위 (서버 명단 순서) */
@Composable
fun DriverPickerDialog(
    roster: List<DriverProfile>,
    currentId: String?,
    onSelect: (DriverProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        title = { Text(stringResource(R.string.driver_picker_title), style = typography.title, color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
            ) {
                Text(stringResource(R.string.driver_picker_hint), style = typography.body, color = colors.textSecondary)
                roster.forEach { driver -> DriverRow(driver, selected = driver.id == currentId, onClick = { onSelect(driver) }) }
            }
        },
        confirmButton = {},
        dismissButton = { DriveLogButton(text = stringResource(R.string.cancel), onClick = onDismiss, kind = ButtonKind.Text) }
    )
}

@Composable
private fun DriverRow(driver: DriverProfile, selected: Boolean, onClick: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMinimum)
            .clip(shape)
            .background(if (selected) colors.buttonSecondaryBackgroundPressed else colors.surface)
            .then(if (selected) Modifier.border(dimens.borderWidth, colors.selectionRing, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spacingLarge, vertical = dimens.spacingSmall)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.spacingExtraSmall)) {
            Text(driver.label, style = DriveLogTheme.typography.titleSmall, color = colors.textPrimary)
            ComfortChips(driver.comfort)
        }
        if (driver.assigned) {
            DriveLogChip(text = stringResource(R.string.driver_assigned), background = colors.statusIntegrityContainer, content = colors.statusIntegrity)
        }
        if (selected) {
            DriveLogChip(text = stringResource(R.string.driver_current), background = colors.surfaceRaised, content = colors.textPrimary, icon = DriveLogIcons.Check)
        }
    }
}

/**
 * 하차 전 확인 (운행을 마친 뒤, 정차 중 홈). 열린 창문·켜진 실내등·배터리 부족을 알리고
 * [한 번에 정리](창문 닫기 + 실내등 끄기), [문 잠그기]를 운전자가 직접 누른다
 */
@Composable
fun ExitCheckBanner(
    check: ExitCheck,
    portrait: Boolean,
    onTidy: (Set<TidyItem>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val shape = RoundedCornerShape(dimens.radiusLarge)
    val attention = check.needsTidy || check.lowBattery || check.failed.isNotEmpty()
    val lines = buildList {
        if (check.openWindows.isNotEmpty()) add(CabinPhrases.windowsSubject(check.openWindows) + " 열려 있어요")
        if (check.cabinLightOn) add(stringResource(R.string.exit_check_light))
        if (check.lowBattery) add(stringResource(R.string.exit_check_battery, check.socPercent ?: 0))
        if (check.tidied.isNotEmpty()) add(CabinPhrases.tidied(check.tidied))
        if (check.failed.isNotEmpty()) add(stringResource(R.string.exit_check_failed))
    }
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            Icon(
                if (attention) DriveLogIcons.Alert else DriveLogIcons.Check,
                contentDescription = null,
                tint = if (attention) colors.statusWarning else colors.statusIntegrity,
                modifier = Modifier.size(dimens.iconSizeMedium)
            )
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingExtraSmall)) {
                Text(
                    stringResource(if (attention) R.string.exit_check_title else R.string.exit_check_done_title),
                    style = typography.title,
                    color = colors.textPrimary
                )
                lines.forEach { Text(it, style = typography.body, color = colors.textSecondary) }
                if (check.openWindows.isNotEmpty()) {
                    Text(stringResource(R.string.exit_check_safety), style = typography.label, color = colors.textTertiary)
                }
            }
        }
    }
    val buttons: @Composable (Modifier) -> Unit = { buttonModifier ->
        Row(modifier = buttonModifier, horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            if (check.needsTidy) {
                DriveLogButton(
                    text = stringResource(R.string.exit_check_tidy),
                    onClick = { onTidy(setOf(TidyItem.WINDOWS, TidyItem.LIGHTS)) },
                    loading = check.tidying,
                    modifier = if (portrait) Modifier.weight(1f) else Modifier
                )
            }
            if (check.doorsLocked == false) {
                DriveLogButton(
                    text = stringResource(R.string.exit_check_lock),
                    onClick = { onTidy(setOf(TidyItem.DOORS)) },
                    kind = ButtonKind.Secondary,
                    enabled = !check.tidying,
                    modifier = if (portrait) Modifier.weight(1f) else Modifier
                )
            }
            DriveLogButton(text = stringResource(R.string.exit_check_close), onClick = onDismiss, kind = ButtonKind.Text)
        }
    }
    val bannerModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (attention) colors.statusWarningContainer else colors.surface)
        .border(dimens.borderWidth, if (attention) colors.statusWarning else colors.border, shape)
        .padding(horizontal = dimens.spacingLarge, vertical = dimens.spacingMedium)
    if (portrait) {
        Column(modifier = bannerModifier, verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
            content()
            buttons(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = bannerModifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { content() }
            buttons(Modifier)
        }
    }
}

private const val OPAQUE = 0xFF shl 24
