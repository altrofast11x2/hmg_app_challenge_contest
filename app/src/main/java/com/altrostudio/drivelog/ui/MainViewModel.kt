package com.altrostudio.drivelog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.altrostudio.drivelog.AppContainer
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.domain.logbook.LogbookBuilder
import com.altrostudio.drivelog.domain.logbook.LogbookSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val monthSummary: LogbookSummary? = null
) {
    val drivingTrip: TripEntity? get() = activeTrip?.takeIf { it.status == TripStatus.DRIVING }
    val parkedTrip: TripEntity? get() = activeTrip?.takeIf { it.status == TripStatus.PARKED }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val container: AppContainer,
    zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val month = YearMonth.now(zone)

    private val activeTripWithSuggestion: Flow<Pair<TripEntity?, TripPurpose?>> =
        container.trips.observeActiveTrip().mapLatest { trip ->
            val address = trip?.takeIf { it.status == TripStatus.PARKED }?.endAddress
            trip to address?.let { container.trips.suggestPurpose(it) }
        }

    private val monthSummary: Flow<LogbookSummary?> = run {
        val (from, to) = LogbookBuilder.monthRange(month, zone)
        container.trips.observeCompletedTrips(from, to).map { LogbookBuilder.build(month, it, zone, driverName = "").summary }
    }

    val state: StateFlow<MainUiState> = combine(
        container.consent.consentGiven,
        container.vehicle.drivingRestricted.onStart { emit(true) }.catch { emit(false) },
        activeTripWithSuggestion,
        container.vehicle.odometerKm.onStart { emit(null) }.catch { emit(null) },
        monthSummary.onStart { emit(null) }
    ) { consent, restricted, (activeTrip, suggestion), odometer, summary ->
        MainUiState(
            consentGiven = consent,
            drivingRestricted = restricted,
            activeTrip = activeTrip,
            odometerKm = odometer,
            suggestion = suggestion,
            month = month,
            monthSummary = summary
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState(month = month))

    val vin: String? get() = container.preferences.vin

    val parkedTimeoutMs: Long get() = container.preferences.parkedTimeoutMs

    fun agree() = container.consent.agree()

    fun classify(purpose: TripPurpose) = container.recorder.classify(purpose)

    fun withdraw() {
        viewModelScope.launch { container.consent.withdraw() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DriveLogApp
                MainViewModel(app.container)
            }
        }
    }
}
