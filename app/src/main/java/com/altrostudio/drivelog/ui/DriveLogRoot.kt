package com.altrostudio.drivelog.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altrostudio.drivelog.R
import com.altrostudio.drivelog.data.sync.SyncOutcome
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.domain.update.LatestVersion
import com.altrostudio.drivelog.ui.components.ButtonKind
import com.altrostudio.drivelog.ui.components.DriveLogButton
import com.altrostudio.drivelog.ui.cabin.DriverPickerDialog
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
import com.altrostudio.drivelog.domain.voice.HelpState
import com.altrostudio.drivelog.domain.voice.HelpTopic
import com.altrostudio.drivelog.ui.cabin.CarStatusScreen
import com.altrostudio.drivelog.ui.help.HelpDialog
import com.altrostudio.drivelog.ui.help.TutorialScreen
import com.altrostudio.drivelog.ui.settings.CabinToggles
import com.altrostudio.drivelog.ui.settings.SettingsScreen
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/** 정차 중에 이동할 수 있는 화면 */
enum class Destination(val parent: Destination?) {
    HOME(null),
    LOGBOOK(HOME),
    SETTINGS(HOME),
    CAR_STATUS(HOME),
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
    val driverCard by viewModel.driverCard.collectAsStateWithLifecycle()
    val exitCheck by viewModel.exitCheck.collectAsStateWithLifecycle()
    val voiceInput by viewModel.voiceInput.collectAsStateWithLifecycle()
    val carStatus by viewModel.carStatus.collectAsStateWithLifecycle()
    val helpState by viewModel.helpState.collectAsStateWithLifecycle()
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(viewModel.tutorialPending) }
    var pickingDriver by rememberSaveable { mutableStateOf(false) }
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
            driverName = driverCard?.current?.name,
            waitingMessage = when {
                !state.consentGiven -> stringResource(R.string.driving_wait_consent)
                onboardingConnect || destination == Destination.CONNECT -> stringResource(R.string.driving_wait_connect)
                else -> null
            },
            modifier = modifier
        )

        !state.consentGiven -> ConsentScreen(
            onAgree = viewModel::agree,
            // 앱을 끝내지 않고 숨긴다 (Pleos CCP 가이드: 최상위에서 나갈 때 moveTaskToBack)
            onExit = { activity?.moveTaskToBack(true) },
            totalSteps = if (viewModel.backendConfigured) 2 else null,
            modifier = modifier
        )

        parkedTrip != null && parkedTrip.id != dismissedTripId -> ClassificationScreen(
            trip = parkedTrip,
            suggestion = state.suggestion,
            timeoutLabel = durationLabel(viewModel.parkedTimeoutMs),
            onSelect = viewModel::classify,
            onLater = {
                viewModel.later(parkedTrip.id)
                dismissedTripId = parkedTrip.id
            },
            modifier = modifier,
            voice = voiceInput,
            onVoice = viewModel::listenNow,
            onVoiceStop = viewModel::stopListening,
            onVoiceConfirm = viewModel::confirmVoice,
            onTyped = viewModel::recordTyped
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
                // 처음 연결할 때만 건너뛸 수 있다. 뒤로가기는 앱을 숨긴다 (design/specs/A02-connect.md, MainActivity)
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

        showTutorial && !onboardingConnect && destination == Destination.HOME -> TutorialScreen(
            onSpeak = viewModel::speak,
            onFinish = {
                viewModel.finishTutorial()
                showTutorial = false
            },
            modifier = modifier
        )

        else -> when (destination) {
            Destination.CAR_STATUS -> CarStatusScreen(
                status = carStatus,
                onBack = { destination = Destination.HOME },
                onRefresh = viewModel::refreshCarStatus,
                onControl = viewModel::controlCar,
                modifier = modifier
            )
            Destination.HOME -> HomeScreen(
                month = state.month,
                summary = state.monthSummary,
                recentTrips = state.recentTrips,
                // [나중에]로 넘긴 방금 운행도 미분류 배너에 넣는다 (넣지 않으면 5분 뒤 미분류로 끝날 때까지 홈에 아무것도 안 보인다)
                // 배너는 오래된 운행부터라 방금 운행은 맨 뒤에 둔다. 지난 미분류를 다 고르면 방금 운행의 분류 화면을 다시 연다
                unclassifiedTrips = state.unclassifiedTrips + listOfNotNull(parkedTrip?.takeIf { it.id == dismissedTripId }),
                sync = syncChipState(state, viewModel.backendConfigured),
                onClassifyNow = {
                    val oldest = state.unclassifiedTrips.firstOrNull()
                    if (oldest != null) classifyingTripId = oldest.id else dismissedTripId = null
                },
                onOpenLogbook = { destination = Destination.LOGBOOK },
                onOpenSettings = { destination = Destination.SETTINGS },
                modifier = modifier,
                onOpenCarStatus = { destination = Destination.CAR_STATUS },
                onOpenHelp = {
                    helpOpen = true
                    viewModel.startHelp()
                },
                driverCard = driverCard,
                exitCheck = exitCheck,
                onConfirmDriver = viewModel::confirmDriver,
                onChangeDriver = { pickingDriver = true },
                onTidy = viewModel::tidyUp,
                onDismissExitCheck = viewModel::dismissExitCheck
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
                    onShare = logbookViewModel::createShareLink,
                    onRetryShare = logbookViewModel::createShareLink,
                    onDismissShare = logbookViewModel::dismissShare,
                    onClassify = logbookViewModel::classify,
                    onExport = logbookViewModel::exportCsv,
                    modifier = modifier
                )
            }
            Destination.SETTINGS -> {
                var voiceGuidance by rememberSaveable { mutableStateOf(viewModel.voiceGuidance) }
                var voiceJournal by rememberSaveable { mutableStateOf(viewModel.voiceJournal) }
                var autoComfort by rememberSaveable { mutableStateOf(viewModel.autoComfort) }
                var ambientCue by rememberSaveable { mutableStateOf(viewModel.ambientCue) }
                var exitCheckOn by rememberSaveable { mutableStateOf(viewModel.exitCheckEnabled) }
                SettingsScreen(
                    vin = viewModel.vin,
                    orgName = state.pairing?.orgName,
                    canConnect = viewModel.backendConfigured,
                    voiceGuidance = voiceGuidance,
                    onVoiceGuidanceChange = {
                        voiceGuidance = it
                        viewModel.setVoiceGuidance(it)
                    },
                    voiceJournal = voiceJournal,
                    onVoiceJournalChange = {
                        voiceJournal = it
                        viewModel.setVoiceJournal(it)
                    },
                    cabin = CabinToggles(
                        autoComfort = autoComfort,
                        ambientCue = ambientCue,
                        exitCheck = exitCheckOn,
                        onAutoComfortChange = {
                            autoComfort = it
                            viewModel.setAutoComfort(it)
                        },
                        onAmbientCueChange = {
                            ambientCue = it
                            viewModel.setAmbientCue(it)
                        },
                        onExitCheckChange = {
                            exitCheckOn = it
                            viewModel.setExitCheckEnabled(it)
                        }
                    ),
                    onBack = { destination = Destination.HOME },
                    onConnect = { destination = Destination.CONNECT },
                    onOpenPrivacy = { destination = Destination.PRIVACY },
                    onOpenLicenses = { destination = Destination.LICENSES },
                    onOpenTutorial = {
                        destination = Destination.HOME
                        showTutorial = true
                    },
                    onWithdraw = {
                        viewModel.withdraw()
                        destination = Destination.HOME
                    },
                    modifier = modifier
                )
            }
            Destination.CONNECT -> Unit // 위에서 처리
            Destination.PRIVACY -> PrivacyPolicyScreen(onBack = { destination = Destination.SETTINGS }, modifier = modifier)
            Destination.LICENSES -> LicensesScreen(onBack = { destination = Destination.SETTINGS }, modifier = modifier)
        }
    }

    // 운전자 고르기 (정차 중 홈에서만)
    val card = driverCard
    if (pickingDriver && card != null && !state.drivingRestricted && destination == Destination.HOME) {
        DriverPickerDialog(
            roster = card.roster,
            currentId = card.current?.id,
            onSelect = { driver ->
                pickingDriver = false
                viewModel.selectDriver(driver.id)
            },
            onDismiss = { pickingDriver = false }
        )
    }

    // 음성 도움 비서 (홈의 [도움]). 답을 읽으면 그 일의 화면으로 데려다준다.
    // 홈이 보일 때만 띄우고, 출발하거나 정차 분류가 뜨면 닫는다 (마이크를 운행 분류와 같이 쓰지 않게)
    val helpAllowed = !state.drivingRestricted && destination == Destination.HOME && !showTutorial &&
        classifyingTripId == null && (parkedTrip == null || parkedTrip.id == dismissedTripId)
    LaunchedEffect(helpAllowed) {
        if (!helpAllowed && helpOpen) {
            helpOpen = false
            viewModel.closeHelp(stopSpeech = false)
        }
    }
    if (helpOpen && helpAllowed) {
        HelpDialog(
            state = helpState,
            onTopic = viewModel::chooseHelp,
            onListen = viewModel::startHelp,
            onDismiss = {
                helpOpen = false
                viewModel.closeHelp()
            }
        )
    }
    val answered = helpState as? HelpState.Answered
    LaunchedEffect(answered) {
        if (answered == null || !helpOpen) return@LaunchedEffect
        helpOpen = false
        when (answered.topic) {
            HelpTopic.CAR_STATUS -> destination = Destination.CAR_STATUS
            HelpTopic.LOGBOOK, HelpTopic.SHARE -> destination = Destination.LOGBOOK
            HelpTopic.DRIVER -> {
                destination = Destination.HOME
                if (driverCard != null) pickingDriver = true
            }
            HelpTopic.VOICE_RECORD -> destination = Destination.HOME
            HelpTopic.TUTORIAL -> {
                destination = Destination.HOME
                showTutorial = true
            }
            HelpTopic.SETTINGS -> destination = Destination.SETTINGS
        }
    }

    // 새 버전 안내는 정차 중 홈에서만 띄운다 (분류·연결 중에는 끼어들지 않는다)
    val update by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val showUpdate = !state.drivingRestricted && state.consentGiven && destination == Destination.HOME &&
        !onboardingConnect && parkedTrip == null && classifyingTripId == null
    update?.takeIf { showUpdate }?.let { UpdateDialog(it, onDismiss = viewModel::dismissUpdate) }
}

/** 새 버전 안내 (Pleos 앱 업데이트 가이드의 문구와 순서: 안내 → 앱 마켓 상세 페이지로 이동) */
@Composable
private fun UpdateDialog(latest: LatestVersion, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val typography = DriveLogTheme.typography
    val colors = DriveLogTheme.colors
    val marketFailed = stringResource(R.string.update_market_unavailable)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        title = { Text(stringResource(R.string.update_title), style = typography.title, color = colors.textPrimary) },
        text = { Text(stringResource(R.string.update_message, latest.versionName), style = typography.body, color = colors.textSecondary) },
        confirmButton = {
            DriveLogButton(
                text = stringResource(R.string.update_open_market),
                onClick = {
                    onDismiss()
                    val detail = Intent(Intent.ACTION_VIEW, Uri.parse("market://detail?package=${context.packageName}"))
                    try {
                        context.startActivity(detail)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, marketFailed, Toast.LENGTH_LONG).show()
                    }
                }
            )
        },
        dismissButton = { DriveLogButton(text = stringResource(R.string.update_later), onClick = onDismiss, kind = ButtonKind.Text) }
    )
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
