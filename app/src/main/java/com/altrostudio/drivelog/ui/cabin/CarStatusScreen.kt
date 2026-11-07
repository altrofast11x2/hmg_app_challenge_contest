package com.altrostudio.drivelog.ui.cabin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.vehicle.CabinWindow
import com.altrostudio.drivelog.domain.cabin.CabinPhrases
import com.altrostudio.drivelog.domain.cabin.CarStatus
import com.altrostudio.drivelog.domain.cabin.TidyItem
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.ScreenFrame
import com.altrostudio.drivelog.ui.components.TopBar
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme
import kotlinx.coroutines.delay

/**
 * 차 상태 보기 (2026-11-08 사용자 요청). 위에서 본 차 그림에 열린 창문을 표시하고, 배터리·계기판·문·실내등·공조·시트 열선·무드등을 보여 준다.
 * P이고 주행 제한이 없으면 [창문 닫기]·[문 잠그기]·[실내등 끄기]를 바로 누를 수 있다 (하차 점검과 같은 안전 조건).
 * 화면이 열려 있는 동안 [REFRESH_MS]마다 다시 읽는다. A10 명세가 나오면 명세대로 바꾼다 (지금은 토큰만 적용)
 */
@Composable
fun CarStatusScreen(
    status: CarStatus?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onControl: (Set<TidyItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = DriveLogTheme.dimens
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(REFRESH_MS)
        }
    }
    ScreenFrame(modifier = modifier) { portrait ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge)
        ) {
            TopBar(title = stringResource(R.string.car_status_title), portrait = portrait, onBack = onBack)
            if (status == null) {
                Text(stringResource(R.string.car_status_loading), style = DriveLogTheme.typography.body, color = DriveLogTheme.colors.textSecondary)
                return@Column
            }
            if (portrait) {
                CarDiagram(status, Modifier.fillMaxWidth())
                StatusPanel(status, onControl, Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
                    CarDiagram(status, Modifier.weight(1f))
                    StatusPanel(status, onControl, Modifier.weight(1.3f))
                }
            }
        }
    }
}

/** 위에서 본 차: 차체와 창문 네 개 (열린 창문은 경고색), 가운데에 문 잠김 */
@Composable
private fun CarDiagram(status: CarStatus, modifier: Modifier) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val open = status.openWindows.orEmpty()
    val shape = RoundedCornerShape(dimens.radiusLarge)
    Column(
        modifier = modifier.clip(shape).background(colors.surface).padding(dimens.spacingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
    ) {
        Text(stringResource(R.string.car_status_front), style = DriveLogTheme.typography.label, color = colors.textTertiary)
        Box(modifier = Modifier.fillMaxWidth(0.55f).aspectRatio(0.5f), contentAlignment = Alignment.Center) {
            val body = colors.surfaceRaised
            val outline = colors.borderStrong
            val closed = colors.border
            val warn = colors.statusWarning
            val glass = colors.surfacePressed
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val radius = CornerRadius(w * 0.32f, w * 0.32f)
                drawRoundRect(color = body, cornerRadius = radius)
                drawRoundRect(color = outline, cornerRadius = radius, style = Stroke(width = w * 0.02f))
                // 앞유리·뒷유리
                drawRoundRect(color = glass, topLeft = Offset(w * 0.16f, h * 0.18f), size = Size(w * 0.68f, h * 0.11f), cornerRadius = CornerRadius(w * 0.06f))
                drawRoundRect(color = glass, topLeft = Offset(w * 0.2f, h * 0.76f), size = Size(w * 0.6f, h * 0.08f), cornerRadius = CornerRadius(w * 0.06f))
                // 옆 창문 네 개 (앞은 운전석·조수석, 뒤는 뒷좌석)
                fun side(window: CabinWindow, x: Float, y: Float) {
                    val color = if (window in open) warn else closed
                    drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(w * 0.07f, h * 0.17f), cornerRadius = CornerRadius(w * 0.03f))
                }
                side(CabinWindow.FRONT_LEFT, w * 0.03f, h * 0.33f)
                side(CabinWindow.FRONT_RIGHT, w * 0.9f, h * 0.33f)
                side(CabinWindow.REAR_LEFT, w * 0.03f, h * 0.53f)
                side(CabinWindow.REAR_RIGHT, w * 0.9f, h * 0.53f)
            }
            val locked = status.doorsLocked
            Text(
                stringResource(R.string.car_status_doors) + " " + stringResource(
                    when (locked) {
                        true -> R.string.car_status_locked
                        false -> R.string.car_status_unlocked
                        null -> R.string.car_status_unknown
                    }
                ),
                style = DriveLogTheme.typography.title,
                color = if (locked == false) colors.statusWarning else colors.textPrimary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingExtraSmall)) {
            Box(Modifier.size(dimens.iconSizeSmall / 2).clip(CircleShape).background(colors.statusWarning))
            Text(stringResource(R.string.car_status_legend_open), style = DriveLogTheme.typography.label, color = colors.textTertiary)
        }
    }
}

@Composable
private fun StatusPanel(status: CarStatus, onControl: (Set<TidyItem>) -> Unit, modifier: Modifier) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val unknown = stringResource(R.string.car_status_unknown)
    val shape = RoundedCornerShape(dimens.radiusLarge)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surface).padding(dimens.spacingMedium),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
        ) {
            val soc = status.socPercent
            StatusRow(stringResource(R.string.car_status_battery), soc?.let { "$it%" } ?: unknown, warn = soc != null && soc < LOW_BATTERY_PERCENT)
            StatusRow(stringResource(R.string.car_status_odometer), status.odometerKm?.let { "%.1f km".format(it) } ?: unknown)
            StatusRow(
                stringResource(R.string.car_status_windows),
                when {
                    status.openWindows == null -> unknown
                    status.openWindows.isEmpty() -> stringResource(R.string.car_status_windows_closed)
                    else -> CabinWindow.entries.filter { it in status.openWindows }.joinToString(" · ") { CabinPhrases.windowName(it) } +
                        " " + stringResource(R.string.car_status_open_suffix)
                },
                warn = status.windowsOpen
            )
            StatusRow(
                stringResource(R.string.car_status_doors),
                when (status.doorsLocked) {
                    true -> stringResource(R.string.car_status_locked)
                    false -> stringResource(R.string.car_status_unlocked)
                    null -> unknown
                },
                warn = status.doorsLocked == false
            )
            StatusRow(
                stringResource(R.string.car_status_cabin_light),
                when (status.cabinLightOn) {
                    true -> stringResource(R.string.car_status_on)
                    false -> stringResource(R.string.car_status_off)
                    null -> unknown
                },
                warn = status.cabinLightOn == true
            )
            StatusRow(stringResource(R.string.car_status_temperature), status.comfort.tempC?.let { CabinPhrases.temperature(it) + "°" } ?: unknown)
            StatusRow(
                stringResource(R.string.car_status_seat),
                status.comfort.seatHeat?.let { level ->
                    when {
                        level > 0 -> stringResource(R.string.car_status_seat_heat, level)
                        level < 0 -> stringResource(R.string.car_status_seat_vent, -level)
                        else -> stringResource(R.string.car_status_off)
                    }
                } ?: unknown
            )
            status.comfort.ambientColor?.let { rgb ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.car_status_ambient), style = DriveLogTheme.typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(dimens.iconSizeSmall).clip(CircleShape)
                            // 무드등 점의 색은 토큰이 아니라 차에서 읽은 실제 조명 색이다 (차량 데이터)
                            .background(Color(0xFF000000 or rgb.toLong()))
                            .border(dimens.borderWidth, colors.border, CircleShape)
                    )
                }
            }
        }
        Actions(status, onControl)
        Text(stringResource(R.string.car_status_refresh_note), style = DriveLogTheme.typography.label, color = colors.textTertiary)
    }
}

@Composable
private fun StatusRow(label: String, value: String, warn: Boolean = false) {
    val colors = DriveLogTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DriveLogTheme.typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = DriveLogTheme.typography.title.copy(fontFeatureSettings = DriveLogTabularNumbers),
            color = if (warn) colors.statusWarning else colors.textPrimary
        )
    }
}

/** P에서만 누를 수 있는 정리 버튼. 주행 중이거나 정리할 것이 없으면 안내만 */
@Composable
private fun Actions(status: CarStatus, onControl: (Set<TidyItem>) -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val actions = status.actions
    when {
        !status.canControl -> Text(stringResource(R.string.car_status_control_parked_only), style = typography.body, color = colors.textSecondary)
        actions.isEmpty() -> Text(stringResource(R.string.car_status_nothing_to_tidy), style = typography.body, color = colors.statusIntegrity)
        else -> Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                if (TidyItem.WINDOWS in actions) {
                    DriveLogButton(stringResource(R.string.car_status_close_windows), { onControl(setOf(TidyItem.WINDOWS)) }, loading = status.busy)
                }
                if (TidyItem.DOORS in actions) {
                    DriveLogButton(stringResource(R.string.car_status_lock_doors), { onControl(setOf(TidyItem.DOORS)) }, kind = ButtonKind.Secondary, enabled = !status.busy)
                }
                if (TidyItem.LIGHTS in actions) {
                    DriveLogButton(stringResource(R.string.car_status_light_off), { onControl(setOf(TidyItem.LIGHTS)) }, kind = ButtonKind.Secondary, enabled = !status.busy)
                }
            }
            if (TidyItem.WINDOWS in actions) {
                Text(stringResource(R.string.exit_check_safety), style = typography.label, color = colors.textTertiary)
            }
        }
    }
    if (status.failed.isNotEmpty()) {
        Text(stringResource(R.string.exit_check_failed), style = typography.body, color = colors.statusError)
    }
}

private const val REFRESH_MS = 5_000L
private const val LOW_BATTERY_PERCENT = 20
