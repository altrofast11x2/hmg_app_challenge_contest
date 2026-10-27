package com.altrostudio.drivelog.ui.logbook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.sync.ShareLinkResult
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.theme.DriveLogLightColors
import com.altrostudio.drivelog.ui.theme.DriveLogTabularNumbers
import com.altrostudio.drivelog.ui.theme.DriveLogTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay

/** 휴대폰으로 받기 상태 */
sealed interface ShareState {
    data object Creating : ShareState
    data class Ready(val url: String, val expiresAtMs: Long) : ShareState
    data class Failed(val result: ShareLinkResult) : ShareState
}

/**
 * 휴대폰으로 받기: QR 코드를 휴대폰 카메라로 찍으면 이 기간의 운행기록부가 휴대폰 브라우저에서 열린다.
 * 차에는 메일·메신저 앱이 없어서 저장·보내기는 휴대폰에서 한다. 링크는 10분 동안만 열린다.
 */
@Composable
fun ShareDialog(share: ShareState, periodTitle: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val qrSize = qrSize()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            modifier = Modifier
                .padding(dimens.spacingLarge)
                .widthIn(max = qrSize * 2)
                .clip(RoundedCornerShape(dimens.radiusLarge))
                .background(colors.surfaceRaised)
                .verticalScroll(rememberScrollState())
                .padding(dimens.spacingMedium)
        ) {
            Text(stringResource(R.string.logbook_share_title), style = typography.title, color = colors.textPrimary)
            Text(periodTitle, style = typography.titleSmall.copy(fontFeatureSettings = DriveLogTabularNumbers), color = colors.textSecondary)
            when (share) {
                ShareState.Creating -> Text(stringResource(R.string.logbook_share_creating), style = typography.body, color = colors.textSecondary)
                is ShareState.Ready -> ReadyContent(share, onRetry)
                is ShareState.Failed -> {
                    Text(failureText(share.result), style = typography.body, color = colors.statusError)
                    DriveLogButton(text = stringResource(R.string.logbook_share_retry), onClick = onRetry, kind = ButtonKind.Secondary)
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                DriveLogButton(text = stringResource(R.string.close), onClick = onDismiss, kind = ButtonKind.Text)
            }
        }
    }
}

@Composable
private fun ReadyContent(share: ShareState.Ready, onRetry: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(share.expiresAtMs) {
        while (now < share.expiresAtMs) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val leftSeconds = ((share.expiresAtMs - now) / 1_000).coerceAtLeast(0)
    if (leftSeconds == 0L) {
        Text(stringResource(R.string.logbook_share_expired), style = typography.body, color = colors.textSecondary)
        DriveLogButton(text = stringResource(R.string.logbook_share_retry), onClick = onRetry, kind = ButtonKind.Secondary)
        return
    }
    // 휴대폰 카메라가 읽기 쉽도록 다크 테마에서도 밝은 바탕에 어두운 점으로 그린다 (라이트 테마 토큰)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(DriveLogLightColors.surface)
            .padding(dimens.spacingMedium)
    ) {
        val qr = remember(share.url) { qrBitmap(share.url, DriveLogLightColors.textPrimary.toArgb(), DriveLogLightColors.surface.toArgb()) }
        Image(
            bitmap = qr,
            contentDescription = stringResource(R.string.logbook_share_qr_description),
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(qrSize())
        )
    }
    Text(
        stringResource(R.string.logbook_share_left, leftSeconds / 60, leftSeconds % 60),
        style = typography.bodyStrong.copy(fontFeatureSettings = DriveLogTabularNumbers),
        color = colors.accent
    )
    Text(stringResource(R.string.logbook_share_hint), style = typography.label, color = colors.textSecondary)
}

/** QR 코드 크기: 최소 터치 크기의 4배 (차 화면에서 휴대폰으로 찍기 쉬운 크기) */
@Composable
private fun qrSize() = DriveLogTheme.dimens.touchTargetMinimum * 4

@Composable
private fun failureText(result: ShareLinkResult): String = when (result) {
    ShareLinkResult.Offline -> stringResource(R.string.logbook_share_failed_offline)
    ShareLinkResult.NotPaired -> stringResource(R.string.logbook_share_failed_not_paired)
    ShareLinkResult.TooMany -> stringResource(R.string.logbook_share_failed_too_many)
    else -> stringResource(R.string.logbook_share_failed)
}

/** QR 코드 비트맵. 한 점이 한 픽셀이고, 화면에서는 FilterQuality.None으로 흐리지 않게 키운다 */
private fun qrBitmap(content: String, dark: Int, light: Int): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) dark else light
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
