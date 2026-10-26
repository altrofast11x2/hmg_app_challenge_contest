package com.altrostudio.drivelog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.sync.SyncOutcome
import com.altrostudio.drivelog.ui.classify.ClassificationScreen
import com.altrostudio.drivelog.ui.connect.ConnectScreen
import com.altrostudio.drivelog.ui.connect.ConnectViewModel
import com.altrostudio.drivelog.ui.consent.ConsentScreen
import com.altrostudio.drivelog.ui.driving.DrivingScreen
import com.altrostudio.drivelog.ui.home.HomeScreen
import com.altrostudio.drivelog.ui.logbook.LogbookScreen
import com.altrostudio.drivelog.ui.logbook.LogbookViewModel
import com.altrostudio.drivelog.ui.settings.LicensesScreen
import com.altrostudio.drivelog.ui.settings.PrivacyPolicyScreen
import com.altrostudio.drivelog.ui.settings.SettingsScreen

/** 정차 중에 이동할 수 있는 화면 */
enum class Destination(val parent: Destination?) {
    HOME(null),
    LOGBOOK(HOME),
    SETTINGS(HOME),
    CONNECT(SETTINGS),
    PRIVACY(SETTINGS),
    LICENSES(SETTINGS)
}

/**
 * 앱 화면 전체. 우선순위:
 * 1. 주행 중이면 무조건 주행 중 화면 (터치 요소 없음)
 * 2. 동의 전이면 동의 화면
 * 3. 정차해서 분류를 기다리는 운행이 있으면 분류 화면
 * 4. 동의 직후 아직 조직에 연결하지 않았으면 연결 화면 ([나중에 연결] 가능)
 * 5. 나머지는 사용자가 고른 화면
 */
@Composable
fun DriveLogRoot(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var dismissedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    val connectViewModel: ConnectViewModel = viewModel(factory = ConnectViewModel.Factory)

    BackHandler(enabled = !state.drivingRestricted && state.consentGiven && destination.parent != null) {
        destination = destination.parent ?: Destination.HOME
    }

    val parkedTrip = state.parkedTrip
    val onboardingConnect = viewModel.backendConfigured && state.pairing == null && !state.pairingSkipped
    when {
        state.drivingRestricted -> DrivingScreen(state.drivingTrip, state.odometerKm, modifier)

        !state.consentGiven -> ConsentScreen(onAgree = viewModel::agree, modifier = modifier)

        parkedTrip != null && parkedTrip.id != dismissedTripId -> ClassificationScreen(
            trip = parkedTrip,
            suggestion = state.suggestion,
            timeoutLabel = durationLabel(viewModel.parkedTimeoutMs),
            onSelect = viewModel::classify,
            onLater = { dismissedTripId = parkedTrip.id },
            modifier = modifier
        )

        onboardingConnect || destination == Destination.CONNECT -> {
            val connectState by connectViewModel.state.collectAsStateWithLifecycle()
            ConnectScreen(
                state = connectState,
                onDigit = connectViewModel::onDigit,
                onBackspace = connectViewModel::onBackspace,
                onClear = connectViewModel::onClear,
                onDone = {
                    connectViewModel.reset()
                    destination = Destination.HOME
                },
                // 처음 연결할 때만 건너뛸 수 있다. 뒤로가기는 앱 종료 (design/specs/A02-connect.md)
                onSkip = if (destination == Destination.CONNECT) null else viewModel::skipPairing,
                onBack = if (destination == Destination.CONNECT) {
                    {
                        connectViewModel.reset()
                        destination = Destination.SETTINGS
                    }
                } else {
                    null
                },
                modifier = modifier
            )
        }

        else -> when (destination) {
            Destination.HOME -> HomeScreen(
                month = state.month,
                summary = state.monthSummary,
                syncLine = syncLine(state, viewModel.backendConfigured),
                onOpenLogbook = { destination = Destination.LOGBOOK },
                onOpenSettings = { destination = Destination.SETTINGS },
                modifier = modifier
            )
            Destination.LOGBOOK -> {
                val driverName = state.pairing?.driverName ?: stringResource(R.string.default_driver_name)
                val logbookViewModel: LogbookViewModel = viewModel(factory = LogbookViewModel.factory(driverName))
                val logbookState by logbookViewModel.state.collectAsStateWithLifecycle()
                LogbookScreen(
                    state = logbookState,
                    onBack = { destination = Destination.HOME },
                    onPreviousMonth = logbookViewModel::previousMonth,
                    onNextMonth = logbookViewModel::nextMonth,
                    onClassify = logbookViewModel::classify,
                    onExport = logbookViewModel::exportCsv,
                    modifier = modifier
                )
            }
            Destination.SETTINGS -> SettingsScreen(
                vin = viewModel.vin,
                orgName = state.pairing?.orgName,
                canConnect = viewModel.backendConfigured,
                onBack = { destination = Destination.HOME },
                onConnect = { destination = Destination.CONNECT },
                onOpenPrivacy = { destination = Destination.PRIVACY },
                onOpenLicenses = { destination = Destination.LICENSES },
                onWithdraw = {
                    viewModel.withdraw()
                    destination = Destination.HOME
                },
                modifier = modifier
            )
            Destination.CONNECT -> Unit // 위에서 처리
            Destination.PRIVACY -> PrivacyPolicyScreen(onBack = { destination = Destination.SETTINGS }, modifier = modifier)
            Destination.LICENSES -> LicensesScreen(onBack = { destination = Destination.SETTINGS }, modifier = modifier)
        }
    }
}

@Composable
private fun durationLabel(millis: Long): String =
    if (millis >= 60_000) stringResource(R.string.duration_minutes, millis / 60_000) else stringResource(R.string.duration_seconds, millis / 1_000)

/** 홈의 조직 연결·동기화 상태 한 줄 */
@Composable
private fun syncLine(state: MainUiState, backendConfigured: Boolean): String {
    val pairing = state.pairing
    return when {
        pairing == null -> stringResource(if (backendConfigured) R.string.sync_not_connected else R.string.sync_local_only)
        state.pendingUploads == 0 -> stringResource(R.string.sync_done, pairing.orgName)
        state.lastSync is SyncOutcome.Offline -> stringResource(R.string.sync_offline, pairing.orgName, state.pendingUploads)
        else -> stringResource(R.string.sync_pending, pairing.orgName, state.pendingUploads)
    }
}
