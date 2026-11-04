package com.altrostudio.drivelog.ui.classify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.DialogProperties
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.purposeColors
import com.altrostudio.drivelog.ui.components.purposeLabel
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/**
 * 말 대신 손으로 용도와 업무 내용을 적는다 (정차 중 분류 화면에서만. 2026-11-05 사용자 요청: 음성 인식이 늦거나 짧은 말을 놓칠 때).
 * 들은 내용이 있으면 미리 채운다. 비업무는 업무 내용을 남기지 않는다
 */
@Composable
fun NoteInputDialog(
    initialPurpose: TripPurpose?,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (TripPurpose, String?) -> Unit
) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    var purpose by rememberSaveable { mutableStateOf(initialPurpose) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    val personal = purpose == TripPurpose.PERSONAL
    AlertDialog(
        onDismissRequest = onDismiss,
        // 차 화면에서 기본 너비는 좁아 글자 칸이 짧다
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        containerColor = colors.surfaceRaised,
        title = { Text(stringResource(R.string.note_dialog_title), style = typography.title, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall), modifier = Modifier.fillMaxWidth()) {
                    for (option in listOf(TripPurpose.COMMUTE, TripPurpose.BUSINESS, TripPurpose.PERSONAL)) {
                        PurposeChoice(option, option == purpose, Modifier.weight(1f)) { purpose = option }
                    }
                }
                OutlinedTextField(
                    value = if (personal) "" else note,
                    onValueChange = { note = it.take(NOTE_MAX) },
                    enabled = !personal,
                    label = { Text(stringResource(R.string.note_dialog_label), style = typography.label) },
                    placeholder = { Text(stringResource(R.string.note_dialog_placeholder), style = typography.body) },
                    textStyle = typography.body,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(dimens.radiusMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        disabledTextColor = colors.textDisabled,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        disabledContainerColor = colors.surface,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.borderStrong,
                        disabledBorderColor = colors.border,
                        focusedLabelColor = colors.accent,
                        unfocusedLabelColor = colors.textSecondary,
                        disabledLabelColor = colors.textDisabled,
                        focusedPlaceholderColor = colors.textTertiary,
                        unfocusedPlaceholderColor = colors.textTertiary,
                        cursorColor = colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = dimens.touchTargetMinimum)
                )
                if (personal) {
                    Text(stringResource(R.string.note_dialog_personal), style = typography.label, color = colors.textTertiary)
                }
            }
        },
        confirmButton = {
            DriveLogButton(
                text = stringResource(R.string.note_dialog_save),
                onClick = { purpose?.let { onSave(it, note.trim().takeIf { text -> text.isNotEmpty() && !personal }) } },
                enabled = purpose != null
            )
        },
        dismissButton = { DriveLogButton(text = stringResource(R.string.cancel), onClick = onDismiss, kind = ButtonKind.Text) }
    )
}

@Composable
private fun PurposeChoice(purpose: TripPurpose, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = DriveLogTheme.colors
    val dimens = DriveLogTheme.dimens
    val (background, content) = purposeColors(purpose)
    val shape = RoundedCornerShape(dimens.radiusMedium)
    Box(
        modifier = modifier
            .heightIn(min = dimens.touchTargetMinimum)
            .then(if (selected) Modifier.border(dimens.borderWidth, colors.selectionRing, shape) else Modifier)
            .clip(shape)
            .background(if (selected) background else colors.surface)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(purposeLabel(purpose), style = DriveLogTheme.typography.title, color = if (selected) content else colors.textSecondary)
    }
}

private const val NOTE_MAX = 200
private const val DIALOG_WIDTH_FRACTION = 0.7f
