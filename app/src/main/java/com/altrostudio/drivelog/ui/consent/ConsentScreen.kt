package com.altrostudio.drivelog.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.ui.components.BigButton
import com.altrostudio.drivelog.ui.theme.Dimens

/**
 * 첫 실행 동의 화면 (정차 중에만 보인다). 수집 항목, 목적, 보유 기간, 거부할 권리를 보여 준다.
 * [동의하고 시작]을 눌렀을 때만 동의로 처리한다. 뒤로가기나 빈 곳 터치는 동의가 아니다.
 */
@Composable
fun ConsentScreen(onAgree: () -> Unit, modifier: Modifier = Modifier) {
    var declined by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)
    ) {
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineMedium)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            Text(stringResource(R.string.consent_intro), style = MaterialTheme.typography.bodyLarge)
            ConsentSection(R.string.consent_items_title, R.string.consent_items)
            ConsentSection(R.string.consent_purpose_title, R.string.consent_purpose)
            ConsentSection(R.string.consent_retention_title, R.string.consent_retention)
            ConsentSection(R.string.consent_refuse_title, R.string.consent_refuse)
        }
        if (declined) {
            Text(
                stringResource(R.string.consent_declined_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            BigButton(
                text = stringResource(R.string.consent_decline),
                onClick = { declined = true },
                emphasized = false,
                modifier = Modifier.weight(1f)
            )
            BigButton(
                text = stringResource(R.string.consent_agree),
                onClick = onAgree,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConsentSection(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.smallGap)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge)
    }
}
