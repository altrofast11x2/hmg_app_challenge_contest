package com.altrostudio.drivelog.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.domain.voice.HelpState
import com.altrostudio.drivelog.domain.voice.HelpTopic
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogIcons
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/**
 * 음성 도움 비서 창 (홈의 [도움]). 열면 "무엇을 도와드릴까요?"라고 묻고 듣는다. 말하지 않아도 아래 버튼으로 고를 수 있다.
 * 답을 읽고 나면 화면은 그 일의 화면으로 이동한다 (DriveLogRoot가 [HelpState.Answered]를 보고 처리)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelpDialog(
    state: HelpState,
    onTopic: (HelpTopic) -> Unit,
    onListen: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val listening = state is HelpState.Listening || state == HelpState.Asking
    val message = when (state) {
        HelpState.Idle -> stringResource(R.string.help_hint)
        HelpState.Asking -> stringResource(R.string.help_asking)
        is HelpState.Listening ->
            if (state.partial.isBlank()) stringResource(R.string.voice_input_listening) else stringResource(R.string.voice_input_listening_text, state.partial)
        is HelpState.Answered -> stringResource(R.string.help_answered)
        is HelpState.NotUnderstood -> stringResource(R.string.help_not_understood, state.heard)
        HelpState.NoSpeech -> stringResource(R.string.help_no_speech)
        HelpState.Unavailable -> stringResource(R.string.help_unavailable)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        containerColor = colors.surfaceRaised,
        title = { Text(stringResource(R.string.help_title), style = typography.title, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                    Icon(
                        DriveLogIcons.Mic,
                        contentDescription = null,
                        tint = if (listening) colors.accent else colors.textTertiary,
                        modifier = Modifier.size(dimens.iconSizeMedium)
                    )
                    Text(message, style = typography.body, color = if (listening) colors.textPrimary else colors.textSecondary)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
                ) {
                    for ((topic, label) in TOPICS) {
                        DriveLogButton(stringResource(label), { onTopic(topic) }, kind = ButtonKind.Secondary)
                    }
                }
            }
        },
        confirmButton = {
            DriveLogButton(
                text = stringResource(R.string.voice_input_retry),
                onClick = onListen,
                kind = ButtonKind.Primary,
                enabled = !listening,
                icon = DriveLogIcons.Mic
            )
        },
        dismissButton = { DriveLogButton(text = stringResource(R.string.help_close), onClick = onDismiss, kind = ButtonKind.Text) }
    )
}

private val TOPICS = listOf(
    HelpTopic.CAR_STATUS to R.string.help_topic_car_status,
    HelpTopic.LOGBOOK to R.string.help_topic_logbook,
    HelpTopic.SHARE to R.string.help_topic_share,
    HelpTopic.DRIVER to R.string.help_topic_driver,
    HelpTopic.VOICE_RECORD to R.string.help_topic_voice,
    HelpTopic.TUTORIAL to R.string.help_topic_tutorial,
    HelpTopic.SETTINGS to R.string.help_topic_settings
)

private const val DIALOG_WIDTH_FRACTION = 0.7f
