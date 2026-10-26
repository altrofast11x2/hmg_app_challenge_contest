package com.altrostudio.drivelog.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.domain.logbook.LogbookSummary
import com.altrostudio.drivelog.ui.theme.DriveLogElevation
import com.altrostudio.drivelog.ui.theme.DriveLogMotion
import com.altrostudio.drivelog.ui.theme.DriveLogTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 공통 컴포넌트 (design/specs/A00-common.md 3절). 색·글자·간격은 DriveLogTheme 토큰만 쓴다.

/** 앱 창 너비가 기준보다 좁으면 2분할(Portrait) 배치 (A00 2절: 화면 방향이 아니라 창 크기로 판단) */
@Composable
fun isPortrait(maxWidth: Dp): Boolean = maxWidth < DriveLogTheme.dimens.portraitBreakpointWidth

/** 화면 바탕과 안쪽 여백. [content]에 Portrait 여부를 넘긴다 */
@Composable
fun ScreenFrame(
    modifier: Modifier = Modifier,
    background: Color = DriveLogTheme.colors.background,
    content: @Composable (portrait: Boolean) -> Unit
) {
    val dimens = DriveLogTheme.dimens
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        val portrait = isPortrait(maxWidth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (portrait) dimens.screenPaddingHorizontalPortrait else dimens.screenPaddingHorizontalLandscape,
                    vertical = dimens.screenPaddingVertical
                )
        ) {
            content(portrait)
        }
    }
}

/** 상단 바: (뒤로) 제목 … 오른쪽 칩·아이콘 버튼 (A00 3.1) */
@Composable
fun TopBar(
    title: String,
    portrait: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
    ) {
        if (onBack != null) IconCircleButton(DriveLogIcons.Back, stringResource(R.string.back), onBack)
        Text(
            title,
            style = if (portrait) typography.title else typography.headline,
            color = DriveLogTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            content = actions
        )
    }
}

/** 제목과 (있으면) 뒤로 버튼이 있는 기본 화면 틀 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ScreenFrame(modifier = modifier) { portrait ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DriveLogTheme.dimens.spacingLarge)) {
            TopBar(title = title, portrait = portrait, onBack = onBack)
            content()
        }
    }
}

/** 누르면 95%로 줄어든다 (A00 3.2, 0.25초 안에 시각 반응) */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource, enabled: Boolean): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) DriveLogMotion.PRESS_SCALE else 1f,
        animationSpec = tween(DriveLogMotion.PRESS_DURATION_MILLIS),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

enum class ButtonKind { Primary, Secondary, Signature, Text }

enum class ButtonSize { Large, Medium }

/** 버튼 (A00 3.2): Primary / Secondary(테두리) / Signature(샌드) / Text. 누름·비활성·로딩 상태를 포함한다 */
@Composable
fun DriveLogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    val isText = kind == ButtonKind.Text
    val background = when {
        !enabled -> if (isText) Color.Transparent else colors.buttonDisabledBackground
        kind == ButtonKind.Primary -> if (pressed) colors.buttonPrimaryBackgroundPressed else colors.buttonPrimaryBackground
        kind == ButtonKind.Secondary -> if (pressed) colors.buttonSecondaryBackgroundPressed else colors.buttonSecondaryBackground
        kind == ButtonKind.Signature -> colors.signature
        else -> if (pressed) colors.surfacePressed else Color.Transparent
    }
    val content = when {
        !enabled -> colors.textDisabled
        kind == ButtonKind.Primary -> colors.buttonPrimaryText
        kind == ButtonKind.Secondary -> colors.buttonSecondaryText
        kind == ButtonKind.Signature -> colors.signatureText
        else -> colors.textSecondary
    }
    val height = when {
        isText -> dimens.touchTargetMinimum
        size == ButtonSize.Large -> dimens.buttonHeightLarge
        else -> dimens.buttonHeightMedium
    }
    val textStyle = when {
        isText -> typography.label
        size == ButtonSize.Large -> typography.title
        else -> typography.titleSmall
    }
    val shape = RoundedCornerShape(if (isText) dimens.radiusFull else dimens.radiusMedium)
    val border = if (kind == ButtonKind.Secondary && enabled) BorderStroke(dimens.borderWidth, colors.borderStrong) else null
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScale(interaction, active)
            .heightIn(min = height)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (isText) dimens.spacingMedium else dimens.spacingExtraLarge)
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = dimens.borderWidth, modifier = Modifier.size(dimens.iconSizeSmall))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(dimens.iconSizeMedium))
                Text(text, style = textStyle, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 기존 화면용 큰 버튼. [emphasized]가 false면 Secondary */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = true
) {
    DriveLogButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        kind = if (emphasized) ButtonKind.Primary else ButtonKind.Secondary,
        enabled = enabled
    )
}

/** 80×80 원형 아이콘 버튼 (A00 3.1) */
@Composable
fun IconCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScale(interaction, enabled = true)
            .size(dimens.touchTargetMinimum)
            .clip(CircleShape)
            .background(if (pressed) colors.buttonSecondaryBackgroundPressed else colors.buttonSecondaryBackground)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(dimens.iconSizeMedium))
    }
}

/** 카드 (A00 3.3): 면 색, 큰 모서리, 테두리 없음. 그림자는 라이트 테마에서만 */
@Composable
fun DriveLogCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = DriveLogTheme.dimens.spacingExtraLarge,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(DriveLogTheme.dimens.spacingLarge),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(DriveLogTheme.dimens.radiusLarge)
    Column(
        modifier = modifier
            .then(if (isSystemInDarkTheme()) Modifier else Modifier.shadow(DriveLogElevation.card, shape))
            .clip(shape)
            .background(DriveLogTheme.colors.surface)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** 배지·칩 (A00 3.4). [text]가 없으면 아이콘만 보이고 [description]을 읽어 준다 */
@Composable
fun DriveLogChip(
    text: String?,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null
) {
    val dimens = DriveLogTheme.dimens
    Row(
        modifier = modifier
            .height(dimens.badgeHeight)
            .clip(RoundedCornerShape(dimens.radiusFull))
            .background(background)
            .padding(horizontal = dimens.spacingSmall)
            .then(if (text == null && description != null) Modifier.semantics { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingExtraSmall)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(dimens.iconSizeSmall))
        if (text != null) Text(text, style = DriveLogTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** 차량 기록 배지: 계기판 값을 차량에서 직접 읽었다는 표시 */
@Composable
fun IntegrityBadge(modifier: Modifier = Modifier, text: String = stringResource(R.string.badge_vehicle_record)) {
    DriveLogChip(
        text = text,
        background = DriveLogTheme.colors.statusIntegrityContainer,
        content = DriveLogTheme.colors.statusIntegrity,
        icon = DriveLogIcons.Shield,
        modifier = modifier
    )
}

/** 용도 칩: 색과 아이콘·글자를 함께 쓴다 (색만으로 구분하지 않는다, A00 3.5) */
@Composable
fun PurposeChip(purpose: TripPurpose, modifier: Modifier = Modifier) {
    val (background, content) = purposeColors(purpose)
    DriveLogChip(text = purposeLabel(purpose), background = background, content = content, icon = purposeIcon(purpose), modifier = modifier)
}

@Composable
fun purposeColors(purpose: TripPurpose): Pair<Color, Color> {
    val colors = DriveLogTheme.colors
    return when (purpose) {
        TripPurpose.COMMUTE -> colors.purposeCommuteBackground to colors.purposeCommuteText
        TripPurpose.BUSINESS -> colors.purposeBusinessBackground to colors.purposeBusinessText
        TripPurpose.PERSONAL -> colors.purposePersonalBackground to colors.purposePersonalText
        TripPurpose.UNCLASSIFIED -> colors.purposeUnclassifiedBackground to colors.purposeUnclassifiedText
    }
}

fun purposeIcon(purpose: TripPurpose): ImageVector = when (purpose) {
    TripPurpose.COMMUTE -> DriveLogIcons.Home
    TripPurpose.BUSINESS -> DriveLogIcons.Work
    TripPurpose.PERSONAL -> DriveLogIcons.Person
    TripPurpose.UNCLASSIFIED -> DriveLogIcons.Help
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

/** 월 요약: 총 주행거리, 업무용 사용거리, 업무사용비율, 미분류 건수 (운행기록부 화면) */
@Composable
fun SummaryCard(summary: LogbookSummary?, compact: Boolean, modifier: Modifier = Modifier) {
    val metrics = listOf(
        stringResource(R.string.summary_total) to stringResource(R.string.km_value, formatKm(summary?.totalKm)),
        stringResource(R.string.summary_work) to stringResource(R.string.km_value, formatKm(summary?.workKm)),
        stringResource(R.string.summary_ratio) to (summary?.workRatioPercent?.let { stringResource(R.string.percent_value, formatOneDecimal(it)) } ?: "-"),
        stringResource(R.string.summary_unclassified) to stringResource(R.string.count_value, summary?.unclassifiedCount ?: 0)
    )
    val dimens = DriveLogTheme.dimens
    DriveLogCard(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
        val rows = if (compact) metrics.chunked(2) else listOf(metrics)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
                row.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = DriveLogTheme.typography.label, color = DriveLogTheme.colors.textSecondary)
                        Text(value, style = DriveLogTheme.typography.title, color = DriveLogTheme.colors.textPrimary)
                    }
                }
            }
        }
    }
}

private val KM_FORMAT = ThreadLocal.withInitial { DecimalFormat("#,##0.0", DecimalFormatSymbols(Locale.ROOT)) }

/** 소수 첫째 자리 km, 천 단위 쉼표 (예: 1,284.6). 값이 없으면 "-" */
fun formatKm(value: Double?): String = value?.let { KM_FORMAT.get()!!.format(it) } ?: "-"

fun formatOneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm")

fun formatTime(epochMillis: Long?): String =
    epochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FORMAT) } ?: "-"

/** 10/26 09:12 */
fun formatDateTime(epochMillis: Long?): String =
    epochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT) } ?: "-"
