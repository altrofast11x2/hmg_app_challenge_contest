package com.altrostudio.drivelog.ui

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
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.domain.logbook.LogbookBuilder
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import com.altrostudio.drivelog.domain.logbook.LogbookSummary
import com.altrostudio.drivelog.domain.logbook.currentMonthFlow
import com.altrostudio.drivelog.domain.trip.TripRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    /** 서버 주소·키가 들어간 빌드인지 (아니면 연결 화면을 건너뛴다) */
    val backendConfigured: Boolean get() = container.pairing.isConfigured

    val vin: String? get() = container.preferences.vin

    val parkedTimeoutMs: Long get() = container.preferences.parkedTimeoutMs

    val voiceGuidance: Boolean get() = container.voiceGuide.enabled

    fun setVoiceGuidance(on: Boolean) = container.voiceGuide.setEnabled(on)

    fun agree() = container.consent.agree()

    fun classify(purpose: TripPurpose) = container.recorder.classify(purpose)

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
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val RECENT_TRIP_COUNT = 4

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DriveLogApp
                MainViewModel(app.container)
            }
        }
    }
}
