package com.altrostudio.drivelog.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.altrostudio.drivelog.AppContainer
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.sync.Pairing
import com.altrostudio.drivelog.data.sync.SyncOutcome
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripLedger
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.domain.cabin.CarStatus
import com.altrostudio.drivelog.domain.voice.HelpState
import com.altrostudio.drivelog.domain.voice.HelpTopic
import com.altrostudio.drivelog.domain.voice.VoiceInputState
import com.altrostudio.drivelog.domain.cabin.CabinPhrases
import com.altrostudio.drivelog.domain.cabin.ExitCheck
import com.altrostudio.drivelog.domain.cabin.TidyItem
import com.altrostudio.drivelog.domain.driver.DriverSource
import com.altrostudio.drivelog.domain.logbook.LogbookBuilder
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import com.altrostudio.drivelog.domain.logbook.LogbookSummary
import com.altrostudio.drivelog.domain.logbook.currentMonthFlow
import com.altrostudio.drivelog.domain.trip.TripRecorder
import com.altrostudio.drivelog.domain.update.LatestVersion
import com.altrostudio.drivelog.ui.cabin.DriverCardState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

data class MainUiState(
    val consentGiven: Boolean = false,
    /** 처음에는 주행 중으로 가정한다 (실제 값을 받기 전에 조작 화면을 보여 주지 않도록) */
    val drivingRestricted: Boolean = true,
    val activeTrip: TripEntity? = null,
    val odometerKm: Float? = null,
    /** 정차 중 운행의 목적지에서 지난번에 고른 업무 분류 */
    val suggestion: TripPurpose? = null,
    val month: YearMonth = YearMonth.now(),
    val monthSummary: LogbookSummary? = null,
    val pairing: Pairing? = null,
    val pairingSkipped: Boolean = false,
    val pendingUploads: Int = 0,
    val lastSync: SyncOutcome? = null,
    /** 최근 완료 운행 (새것부터, 홈의 최근 운행 카드) */
    val recentTrips: List<TripEntity> = emptyList(),
    /** 분류하지 않은 완료 운행 (오래된 것부터, 홈의 분류 배너와 분류 순서) */
    val unclassifiedTrips: List<TripEntity> = emptyList(),
    /** 완료 운행 목록을 처음 받기 전 (홈 로딩 표시) */
    val tripsLoaded: Boolean = false
) {
    val drivingTrip: TripEntity? get() = activeTrip?.takeIf { it.status == TripStatus.DRIVING }
    val parkedTrip: TripEntity? get() = activeTrip?.takeIf { it.status == TripStatus.PARKED }
}

private data class SyncInfo(val pairing: Pairing?, val skipped: Boolean, val pending: Int, val lastOutcome: SyncOutcome?)

private data class TripLists(val recent: List<TripEntity>, val unclassified: List<TripEntity>)

private data class MonthSummary(val month: YearMonth, val summary: LogbookSummary)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val container: AppContainer,
    zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    // 앱을 며칠씩 켜 둔 채 써도 달이 바뀌면 홈의 "이번 달"도 새 달로 바뀐다
    private val currentMonth: StateFlow<YearMonth> =
        currentMonthFlow(zone).stateIn(viewModelScope, SharingStarted.Eagerly, YearMonth.now(zone))

    private val activeTripWithSuggestion: Flow<Pair<TripEntity?, TripPurpose?>> =
        container.trips.observeActiveTrip().mapLatest { trip ->
            val address = trip?.takeIf { it.status == TripStatus.PARKED }?.endAddress
            trip to address?.let { container.trips.suggestPurpose(it) }
        }

    private val monthSummary: Flow<MonthSummary?> = currentMonth.flatMapLatest { month ->
        val period = LogbookPeriod.ofMonth(month)
        val (from, to) = period.range(zone)
        container.trips.observeCompletedTrips(from, to)
            .map { MonthSummary(month, LogbookBuilder.build(period, it, zone, driverName = "").summary) }
    }

    private val syncInfo: Flow<SyncInfo> = combine(
        container.pairing.pairing,
        container.pairing.skipped,
        container.trips.observePendingCount().onStart { emit(0) },
        container.sync.lastOutcome
    ) { pairing, skipped, pending, lastOutcome -> SyncInfo(pairing, skipped, pending, lastOutcome) }

    private val tripLists: Flow<TripLists> = container.trips.observeTrips().map { trips ->
        val completed = trips.filter { it.status == TripStatus.COMPLETED }
        TripLists(
            recent = completed.sortedByDescending { it.startedAt }.take(RECENT_TRIP_COUNT),
            unclassified = completed.filter { it.purpose == TripPurpose.UNCLASSIFIED }.sortedBy { it.startedAt }
        )
    }

    val state: StateFlow<MainUiState> = combine(
        container.consent.consentGiven,
        container.vehicle.drivingRestricted.onStart { emit(true) }.catch { emit(false) },
        activeTripWithSuggestion,
        container.vehicle.odometerKm.onStart { emit(null) }.catch { emit(null) },
        monthSummary.onStart { emit(null) }
    ) { consent, restricted, (activeTrip, suggestion), odometer, monthly ->
        MainUiState(
            consentGiven = consent,
            drivingRestricted = restricted,
            activeTrip = activeTrip,
            odometerKm = odometer,
            suggestion = suggestion,
            month = monthly?.month ?: currentMonth.value,
            monthSummary = monthly?.summary
        )
    }.combine(syncInfo) { main, sync ->
        main.copy(pairing = sync.pairing, pairingSkipped = sync.skipped, pendingUploads = sync.pending, lastSync = sync.lastOutcome)
    }.combine(tripLists.onStart { emit(TripLists(emptyList(), emptyList())) }) { main, lists ->
        main.copy(recentTrips = lists.recent, unclassifiedTrips = lists.unclassified, tripsLoaded = main.monthSummary != null)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        MainUiState(month = currentMonth.value, pairing = container.pairing.pairing.value, pairingSkipped = container.pairing.skipped.value)
    )

    private val _availableUpdate = MutableStateFlow<LatestVersion?>(null)

    /** 앱 마켓에 올라온 새 버전 (Pleos 앱 업데이트 가이드: 앱을 열 때 확인해 안내한다) */
    val availableUpdate: StateFlow<LatestVersion?> = _availableUpdate.asStateFlow()

    init {
        // 동의하기 전에는 아무 곳과도 통신하지 않는다
        if (container.consent.consentGiven.value) viewModelScope.launch { _availableUpdate.value = container.updateChecker.check() }
    }

    fun dismissUpdate() {
        _availableUpdate.value = null
    }

    // ─── 공용차 운전자 ──────────────────────────────────────────

    private val driverApplying = MutableStateFlow(false)
    private val driverMessage = MutableStateFlow<String?>(null)

    /** 운전자를 다시 확인할 때가 되었는지 1분마다 다시 본다 */
    private val minuteTicks = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    /** 홈 운전자 카드. 조직에 연결하지 않았거나 명단이 없으면 null (카드를 보이지 않는다) */
    val driverCard: StateFlow<DriverCardState?> = combine(
        container.drivers.enabled,
        container.drivers.current,
        container.drivers.roster,
        combine(driverApplying, driverMessage) { applying, message -> applying to message },
        minuteTicks
    ) { enabled, current, roster, (applying, message), _ ->
        if (!enabled) {
            null
        } else {
            DriverCardState(current, roster, container.drivers.needsConfirmation(), applying, message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** 운전자를 고른다: 운행기록부 사용자 칸 + 그분의 맞춤 설정으로 차를 맞추고 소리로 알린다 */
    fun selectDriver(driverId: String) {
        viewModelScope.launch {
            driverApplying.value = true
            val selection = try {
                container.drivers.select(driverId, DriverSource.TOUCH)
            } finally {
                driverApplying.value = false
            }
            selection ?: return@launch
            val text = CabinPhrases.applied(selection.driver.name, selection.applied)
            driverMessage.value = text
            container.voiceGuide.say(text)
            delay(MESSAGE_MS)
            if (driverMessage.value == text) driverMessage.value = null
        }
    }

    /** [맞아요]: 지금 운전자를 확인하고 그분의 설정을 다시 맞춘다 */
    fun confirmDriver() {
        container.drivers.current.value?.let { selectDriver(it.id) }
    }

    // ─── 하차 점검 ──────────────────────────────────────────

    val exitCheck: StateFlow<ExitCheck?> get() = container.cabinAssistant.exitCheck

    fun tidyUp(items: Set<TidyItem>) {
        viewModelScope.launch { container.cabinAssistant.tidyUp(items) }
    }

    fun dismissExitCheck() = container.cabinAssistant.dismissExitCheck()

    // ─── 설정: 차 안 연동 ──────────────────────────────────────

    val autoComfort: Boolean get() = container.preferences.autoComfort

    fun setAutoComfort(on: Boolean) = container.drivers.setAutoComfort(on)

    val ambientCue: Boolean get() = container.preferences.ambientCue

    fun setAmbientCue(on: Boolean) {
        container.preferences.ambientCue = on
    }

    val exitCheckEnabled: Boolean get() = container.preferences.exitCheck

    fun setExitCheckEnabled(on: Boolean) {
        container.preferences.exitCheck = on
        if (!on) container.cabinAssistant.dismissExitCheck()
    }

    /** 서버 주소·키가 들어간 빌드인지 (아니면 연결 화면을 건너뛴다) */
    val backendConfigured: Boolean get() = container.pairing.isConfigured

    val vin: String? get() = container.preferences.vin

    val parkedTimeoutMs: Long get() = container.preferences.parkedTimeoutMs

    val voiceGuidance: Boolean get() = container.voiceGuide.enabled

    fun setVoiceGuidance(on: Boolean) = container.voiceGuide.setEnabled(on)

    /** 말로 용도·업무 내용 기록 (분류 화면) */
    val voiceInput: StateFlow<VoiceInputState> get() = container.voiceJournal.state

    fun listenNow() = container.voiceJournal.listenNow()

    fun stopListening() = container.voiceJournal.cancel()

    /** 정차 화면의 [나중에]: 듣기를 멈추고, 내리기 전에 볼 수 있게 하차 점검을 바로 한다 */
    fun later(tripId: String) {
        container.voiceJournal.cancel()
        container.cabinAssistant.checkBeforeLeaving(tripId)
    }

    val voiceJournal: Boolean get() = container.voiceJournal.enabled

    fun setVoiceJournal(on: Boolean) = container.voiceJournal.setEnabled(on)

    fun agree() = container.consent.agree()

    /** 말로 기록을 확인하는 중이면 그 업무 내용을 살려 고른 용도로 기록한다 */
    fun classify(purpose: TripPurpose) {
        if (!container.voiceJournal.confirm(purpose)) container.recorder.classify(purpose)
    }

    /** 직접 입력한 용도·업무 내용으로 기록한다 (운전자가 손으로 적었으므로 수정 이력은 운전자, 안내는 용도만 읽음) */
    fun recordTyped(purpose: TripPurpose, note: String?) {
        container.voiceJournal.cancel()
        viewModelScope.launch {
            container.recorder.recordByVoice(
                purpose,
                note,
                TripLedger.driverActor(container.drivers.current.value?.id),
                readBackNote = false
            )
        }
    }

    private val _carStatus = MutableStateFlow<CarStatus?>(null)

    /** 차 상태 보기 화면의 값 (열려 있는 동안 5초마다 새로 읽는다) */
    val carStatus: StateFlow<CarStatus?> = _carStatus.asStateFlow()

    fun refreshCarStatus() {
        viewModelScope.launch {
            if (_carStatus.value?.busy == true) return@launch
            try {
                _carStatus.value = container.cabinAssistant.readStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "차 상태 읽기 실패: ${e.javaClass.simpleName}")
            }
        }
    }

    /** 차 상태 보기의 [창문 닫기]·[문 잠그기]·[실내등 끄기]. 실패해도 버튼이 '바꾸는 중'에 멈추지 않게 한다 */
    fun controlCar(items: Set<TidyItem>) {
        viewModelScope.launch {
            _carStatus.value = _carStatus.value?.copy(busy = true, failed = emptySet())
            try {
                _carStatus.value = container.cabinAssistant.control(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "차 조작 실패: ${e.javaClass.simpleName}")
                _carStatus.value = _carStatus.value?.copy(busy = false, failed = items)
            }
        }
    }

    /** 음성 도움 비서 */
    val helpState: StateFlow<HelpState> get() = container.helpAssistant.state

    fun startHelp() = container.helpAssistant.start()

    fun chooseHelp(topic: HelpTopic) = container.helpAssistant.choose(topic)

    fun closeHelp(stopSpeech: Boolean = true) = container.helpAssistant.cancel(stopSpeech)

    /** 동의·연결 뒤 처음이면 사용법을 보여 준다 */
    val tutorialPending: Boolean get() = !container.preferences.tutorialSeen

    fun finishTutorial() {
        container.preferences.tutorialSeen = true
        // [건너뛰기]·[시작하기]를 누르면 읽던 사용법을 멈춘다
        container.voiceGuide.stop()
    }

    /** 사용법을 읽어 준다 (음성 안내를 껐으면 읽지 않는다) */
    fun speak(text: String) = container.voiceGuide.say(text)

    /** 말로 기록 확인의 [맞아요] */
    fun confirmVoice() {
        container.voiceJournal.confirm()
    }

    /** 미분류로 끝난 지난 운행을 분류한다 (홈의 [지금 분류하기]) */
    fun classifyPast(tripId: String, purpose: TripPurpose) {
        viewModelScope.launch { container.trips.classify(tripId, purpose, TripRecorder.DRIVER_ACTOR, System.currentTimeMillis()) }
    }

    /** 같은 목적지에서 지난번에 고른 업무 분류 */
    suspend fun suggestionFor(trip: TripEntity): TripPurpose? = trip.endAddress?.let { container.trips.suggestPurpose(it) }

    fun skipPairing() = container.pairing.skip()

    fun withdraw() {
        viewModelScope.launch { container.consent.withdraw() }
    }

    companion object {
        private const val TAG = "DriveLog/Main"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 60_000L

        /** 맞춤 적용 결과 문장을 카드에 보여 주는 시간 */
        private const val MESSAGE_MS = 8_000L
        private const val RECENT_TRIP_COUNT = 4

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DriveLogApp
                MainViewModel(app.container)
            }
        }
    }
}
