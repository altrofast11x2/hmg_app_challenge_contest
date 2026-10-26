package com.altrostudio.drivelog.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.altrostudio.drivelog.AppContainer
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.trip.ChainVerification
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.domain.logbook.Logbook
import com.altrostudio.drivelog.domain.logbook.LogbookBuilder
import com.altrostudio.drivelog.domain.logbook.LogbookCsv
import com.altrostudio.drivelog.domain.logbook.LogbookMonths
import com.altrostudio.drivelog.domain.logbook.MonthChoice
import com.altrostudio.drivelog.domain.trip.TripRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

sealed interface Integrity {
    data object Checking : Integrity
    data class Valid(val count: Int) : Integrity
    data class Broken(val atSeq: Long, val reason: String) : Integrity
}

sealed interface ExportResult {
    data class Saved(val path: String) : ExportResult
    data class Failed(val message: String) : ExportResult
}

data class LogbookUiState(
    val month: YearMonth,
    val logbook: Logbook? = null,
    val integrity: Integrity = Integrity.Checking,
    val exporting: Boolean = false,
    val exportResult: ExportResult? = null,
    /** 바로 고를 수 있는 달 (오래된 달부터, 이번 달까지) */
    val months: List<MonthChoice> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModel(
    private val container: AppContainer,
    private val driverName: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now(zone))
    private val _state = MutableStateFlow(LogbookUiState(month.value))
    val state: StateFlow<LogbookUiState> = _state.asStateFlow()

    init {
        month.flatMapLatest { selected ->
            val (from, to) = LogbookBuilder.monthRange(selected, zone)
            container.trips.observeCompletedTrips(from, to).map { LogbookBuilder.build(selected, it, zone, driverName) }
        }.onEach { logbook ->
            _state.update { it.copy(month = logbook.month, logbook = logbook) }
            verifyIntegrity()
        }.launchIn(viewModelScope)

        container.trips.observeTrips().map { trips ->
            val started = trips.filter { it.status == TripStatus.COMPLETED }.map { it.startedAt }
            LogbookMonths.choices(started, YearMonth.now(zone), zone)
        }.onEach { months -> _state.update { it.copy(months = months) } }.launchIn(viewModelScope)
    }

    /** 월 목록에서 고른 달로 바로 이동한다 */
    fun selectMonth(selected: YearMonth) = changeMonth(selected)

    /** 미분류였던 지난 운행을 분류한다 (정차 중에만 보이는 화면) */
    fun classify(tripId: String, purpose: TripPurpose) {
        viewModelScope.launch { container.trips.classify(tripId, purpose, TripRecorder.DRIVER_ACTOR, now()) }
    }

    fun exportCsv() {
        val logbook = state.value.logbook ?: return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, exportResult = null) }
            val result = try {
                ExportResult.Saved(container.csvSaver.save(LogbookCsv.fileName(logbook), LogbookCsv.build(logbook)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportResult.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { it.copy(exporting = false, exportResult = result) }
        }
    }

    private fun changeMonth(selected: YearMonth) {
        month.value = selected
        _state.update { it.copy(month = selected, exportResult = null) }
    }

    private fun verifyIntegrity() {
        viewModelScope.launch {
            _state.update { it.copy(integrity = Integrity.Checking) }
            val integrity = when (val result = container.trips.verifyChain(container.vehicleIdentity.vehicleId())) {
                is ChainVerification.Valid -> Integrity.Valid(result.count)
                is ChainVerification.Broken -> Integrity.Broken(result.atSeq, result.reason)
            }
            _state.update { it.copy(integrity = integrity) }
        }
    }

    companion object {
        fun factory(driverName: String) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DriveLogApp
                LogbookViewModel(app.container, driverName)
            }
        }
    }
}
