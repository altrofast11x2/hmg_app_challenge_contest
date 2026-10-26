package com.altrostudio.drivelog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.components.DriveLogCard
import com.altrostudio.drivelog.ui.components.ScreenScaffold
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/** 설정: 차량 정보, 조직 연결, 개인정보 처리방침, 오픈소스 라이선스, 동의 철회. A08 명세가 나오면 명세대로 바꾼다 */
@Composable
fun SettingsScreen(
    vin: String?,
    orgName: String?,
    canConnect: Boolean,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmingWithdraw by rememberSaveable { mutableStateOf(false) }
    val dimens = DriveLogTheme.dimens
    val typography = DriveLogTheme.typography
    val colors = DriveLogTheme.colors
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
        ) {
            DriveLogCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                Text(stringResource(R.string.settings_vehicle), style = typography.title, color = colors.textPrimary)
                Text(
                    if (vin != null) stringResource(R.string.settings_vehicle_vin, vin) else stringResource(R.string.settings_vehicle_device),
                    style = typography.body,
                    color = colors.textSecondary
                )
            }
            DriveLogCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                Text(stringResource(R.string.settings_org), style = typography.title, color = colors.textPrimary)
                Text(
                    if (orgName != null) stringResource(R.string.settings_org_connected, orgName) else stringResource(R.string.settings_org_none),
                    style = typography.body,
                    color = colors.textSecondary
                )
                if (canConnect) {
                    DriveLogButton(
                        text = stringResource(if (orgName != null) R.string.settings_reconnect else R.string.settings_connect),
                        onClick = onConnect,
                        kind = if (orgName == null) ButtonKind.Primary else ButtonKind.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            DriveLogButton(stringResource(R.string.settings_privacy), onOpenPrivacy, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            DriveLogButton(stringResource(R.string.settings_licenses), onOpenLicenses, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            DriveLogButton(stringResource(R.string.settings_withdraw), { confirmingWithdraw = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        }
    }
    if (confirmingWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmingWithdraw = false },
            containerColor = colors.surfaceRaised,
            title = { Text(stringResource(R.string.withdraw_confirm_title), style = typography.title, color = colors.textPrimary) },
            text = { Text(stringResource(R.string.withdraw_confirm_message), style = typography.body, color = colors.textSecondary) },
            confirmButton = {
                DriveLogButton(
                    text = stringResource(R.string.withdraw_confirm),
                    onClick = {
                        confirmingWithdraw = false
                        onWithdraw()
                    }
                )
            },
            dismissButton = { DriveLogButton(text = stringResource(R.string.cancel), onClick = { confirmingWithdraw = false }, kind = ButtonKind.Text) }
        )
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = stringResource(R.string.privacy_title), onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.privacy_body),
            style = DriveLogTheme.typography.body,
            color = DriveLogTheme.colors.textSecondary,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}
