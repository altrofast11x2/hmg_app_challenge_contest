package com.altrostudio.drivelog.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.sync.SyncOutcome
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.ui.classify.ClassificationScreen
import com.altrostudio.drivelog.ui.connect.ConnectScreen
import com.altrostudio.drivelog.ui.connect.ConnectViewModel
import com.altrostudio.drivelog.ui.consent.ConsentScreen
import com.altrostudio.drivelog.ui.driving.DrivingScreen
import com.altrostudio.drivelog.ui.home.HomeScreen
import com.altrostudio.drivelog.ui.home.SyncChipState
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
 * 4. 홈의 [지금 분류하기]로 미분류 운행을 오래된 것부터 분류하는 중이면 분류 화면
 * 5. 동의 직후 아직 조직에 연결하지 않았으면 연결 화면 ([나중에 연결] 가능)
 * 6. 나머지는 사용자가 고른 화면
 */
@Composable
fun DriveLogRoot(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var dismissedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    // 홈의 [지금 분류하기]: 지금 분류 중인 미분류 운행
    var classifyingTripId by rememberSaveable { mutableStateOf<String?>(null) }
    val connectViewModel: ConnectViewModel = viewModel(factory = ConnectViewModel.Factory)
    val activity = LocalActivity.current

    BackHandler(enabled = !state.drivingRestricted && state.consentGiven && destination.parent != null) {
        destination = destination.parent ?: Destination.HOME
    }
    BackHandler(enabled = !state.drivingRestricted && classifyingTripId != null) { classifyingTripId = null }

    val parkedTrip = state.parkedTrip
    val queueTrip = classifyingTripId?.let { id -> state.unclassifiedTrips.find { it.id == id } }
    val queueSuggestion by produceState<TripPurpose?>(initialValue = null, queueTrip?.id) {
        value = queueTrip?.let { viewModel.suggestionFor(it) }
    }
    val onboardingConnect = viewModel.backendConfigured && state.pairing == null && !state.pairingSkipped
    when {
        // 동의 전·연결 전에 출발하면 "정차 후 …할 수 있어요" (A01·A02 주행 중 변형)
        state.drivingRestricted -> DrivingScreen(
            drivingTrip = state.drivingTrip,
            odometerKm = state.odometerKm,
            waitingMessage = when {
                !state.consentGiven -> stringResource(R.string.driving_wait_consent)
                onboardingConnect || destination == Destination.CONNECT -> stringResource(R.string.driving_wait_connect)
                else -> null
            },
            modifier = modifier
        )

        !state.consentGiven -> ConsentScreen(
            onAgree = viewModel::agree,
            onExit = { activity?.finish() },
            totalSteps = if (viewModel.backendConfigured) 2 else null,
            modifier = modifier
        )

        parkedTrip != null && parkedTrip.id != dismissedTripId -> ClassificationScreen(
            trip = parkedTrip,
            suggestion = state.suggestion,
            timeoutLabel = durationLabel(viewModel.parkedTimeoutMs),
            onSelect = viewModel::classify,
            onLater = { dismissedTripId = parkedTrip.id },
            modifier = modifier
        )

        queueTrip != null -> ClassificationScreen(
            trip = queueTrip,
            suggestion = queueSuggestion,
            timeoutLabel = null,
            onSelect = { purpose ->
                viewModel.classifyPast(queueTrip.id, purpose)
                classifyingTripId = state.unclassifiedTrips.firstOrNull { it.id != queueTrip.id }?.id
            },
            onLater = { classifyingTripId = null },
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
                recentTrips = state.recentTrips,
                unclassifiedTrips = state.unclassifiedTrips,
                sync = syncChipState(state, viewModel.backendConfigured),
                onClassifyNow = { classifyingTripId = state.unclassifiedTrips.firstOrNull()?.id },
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
                    onSelectMonth = logbookViewModel::selectMonth,
                    onSelectPeriod = logbookViewModel::selectPeriod,
                    connectedOrgName = state.pairing?.orgName,
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

/** 홈의 동기화 칩 (design/specs/A00-common.md 3.4) */
private fun syncChipState(state: MainUiState, backendConfigured: Boolean): SyncChipState = when {
    state.pairing == null -> if (backendConfigured) SyncChipState.NotConnected else SyncChipState.LocalOnly
    state.pendingUploads == 0 -> SyncChipState.Synced
    state.lastSync is SyncOutcome.Offline -> SyncChipState.Offline
    else -> SyncChipState.Pending(state.pendingUploads)
}
