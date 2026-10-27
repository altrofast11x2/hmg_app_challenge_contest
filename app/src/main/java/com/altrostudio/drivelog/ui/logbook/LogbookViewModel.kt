package com.altrostudio.drivelog.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.altrostudio.drivelog.AppContainer
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.sync.ShareLinkResult
import com.altrostudio.drivelog.data.trip.ChainVerification
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.domain.logbook.Logbook
import com.altrostudio.drivelog.domain.logbook.LogbookBuilder
import com.altrostudio.drivelog.domain.logbook.LogbookCsv
import com.altrostudio.drivelog.domain.logbook.LogbookMonths
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import com.altrostudio.drivelog.domain.logbook.MonthChoice
import com.altrostudio.drivelog.domain.logbook.currentMonthFlow
import com.altrostudio.drivelog.domain.trip.TripRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
    /** 보고 있는 기간. 달 칩을 고르면 그 달 전체, 달력에서 고르면 고른 날짜들 */
    val period: LogbookPeriod,
    val logbook: Logbook? = null,
    val integrity: Integrity = Integrity.Checking,
    val exporting: Boolean = false,
    val exportResult: ExportResult? = null,
    /** 바로 고를 수 있는 달 (오래된 달부터, 이번 달까지) */
    val months: List<MonthChoice> = emptyList(),
    /** 휴대폰으로 받기 QR 코드 (null이면 닫힘) */
    val share: ShareState? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModel(
    private val container: AppContainer,
    private val driverName: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    // 앱을 며칠씩 켜 둔 채 쓰는 차에서도 달 목록과 "이번 달"이 달이 바뀌면 따라온다
    private val currentMonth: StateFlow<YearMonth> =
        currentMonthFlow(zone).stateIn(viewModelScope, SharingStarted.Eagerly, YearMonth.now(zone))
    private val period = MutableStateFlow(LogbookPeriod.ofMonth(currentMonth.value))
    private val _state = MutableStateFlow(LogbookUiState(period.value))
    val state: StateFlow<LogbookUiState> = _state.asStateFlow()

    init {
        period.flatMapLatest { selected ->
            val (from, to) = selected.range(zone)
            container.trips.observeCompletedTrips(from, to).map { LogbookBuilder.build(selected, it, zone, driverName) }
        }.onEach { logbook ->
            _state.update { it.copy(period = logbook.period, logbook = logbook) }
            verifyIntegrity()
        }.launchIn(viewModelScope)

        combine(container.trips.observeTrips(), currentMonth) { trips, month ->
            val started = trips.filter { it.status == TripStatus.COMPLETED }.map { it.startedAt }
            LogbookMonths.choices(started, month, zone)
        }.onEach { months -> _state.update { it.copy(months = months) } }.launchIn(viewModelScope)

        // 이번 달을 보고 있었으면 새 달로 넘어간다. 직접 고른 다른 기간은 그대로 둔다
        var shownMonth = currentMonth.value
        currentMonth.onEach { month ->
            if (month != shownMonth && period.value == LogbookPeriod.ofMonth(shownMonth)) changePeriod(LogbookPeriod.ofMonth(month))
            shownMonth = month
        }.launchIn(viewModelScope)
    }

    /** 월 목록에서 고른 달로 바로 이동한다 */
    fun selectMonth(selected: YearMonth) = changePeriod(LogbookPeriod.ofMonth(selected))

    /** 달력에서 고른 기간으로 바꾼다 (사용자 피드백 2026-10-27) */
    fun selectPeriod(selected: LogbookPeriod) = changePeriod(selected)

    /** 미분류였던 지난 운행을 분류한다 (정차 중에만 보이는 화면) */
    fun classify(tripId: String, purpose: TripPurpose) {
        viewModelScope.launch { container.trips.classify(tripId, purpose, TripRecorder.DRIVER_ACTOR, now()) }
    }

    /** 휴대폰으로 받기: 보고 있는 기간의 운행기록부를 휴대폰에서 여는 QR 코드 링크를 만든다 */
    fun createShareLink() {
        val selected = period.value
        viewModelScope.launch {
            _state.update { it.copy(share = ShareState.Creating) }
            val share = when (val result = container.shareLinks.create(selected, zone)) {
                is ShareLinkResult.Ready -> ShareState.Ready(result.url, result.expiresAtMs)
                else -> ShareState.Failed(result)
            }
            _state.update { it.copy(share = share) }
        }
    }

    fun dismissShare() = _state.update { it.copy(share = null) }

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

    private fun changePeriod(selected: LogbookPeriod) {
        period.value = selected
        _state.update { it.copy(period = selected, exportResult = null) }
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
