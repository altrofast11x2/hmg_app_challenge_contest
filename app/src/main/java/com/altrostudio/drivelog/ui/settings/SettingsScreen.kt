package com.altrostudio.drivelog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.BigButton
import com.altrostudio.drivelog.ui.components.ScreenScaffold
import com.altrostudio.drivelog.ui.theme.Dimens

@Composable
fun SettingsScreen(
    vin: String?,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmingWithdraw by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            Text(stringResource(R.string.settings_vehicle), style = MaterialTheme.typography.titleLarge)
            Text(
                if (vin != null) stringResource(R.string.settings_vehicle_vin, vin) else stringResource(R.string.settings_vehicle_device),
                style = MaterialTheme.typography.bodyLarge
            )
            BigButton(stringResource(R.string.settings_privacy), onOpenPrivacy, Modifier.fillMaxWidth(), emphasized = false)
            BigButton(stringResource(R.string.settings_licenses), onOpenLicenses, Modifier.fillMaxWidth(), emphasized = false)
            BigButton(stringResource(R.string.settings_withdraw), { confirmingWithdraw = true }, Modifier.fillMaxWidth(), emphasized = false)
        }
    }
    if (confirmingWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmingWithdraw = false },
            title = { Text(stringResource(R.string.withdraw_confirm_title)) },
            text = { Text(stringResource(R.string.withdraw_confirm_message), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingWithdraw = false
                    onWithdraw()
                }) { Text(stringResource(R.string.withdraw_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingWithdraw = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = stringResource(R.string.privacy_title), onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}
