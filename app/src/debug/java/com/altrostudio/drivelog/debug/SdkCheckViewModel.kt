package com.altrostudio.drivelog.debug

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.altrostudio.drivelog.AppContainer
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.navi.NaviPlace
import com.altrostudio.drivelog.data.settings.AppPreferences
import com.altrostudio.drivelog.data.trip.HashChain
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.distanceKm
import com.altrostudio.drivelog.domain.trip.TripState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SdkCheckState(
    val odometer: String = "-",
    val gear: String = "-",
    val parkingBrake: String = "-",
    val drivingRestricted: Boolean = false,
    val vin: String = "-",
    val efficiency: String = "-",
    val battery: String = "-",
    val currentPlace: String = "-",
    val destination: String = "-",
    val recorderState: String = "-",
    val canClassify: Boolean = false,
    val consentGiven: Boolean = false,
    val parkedTimeout: String = "-",
    val chain: String = "-",
    val trips: List<String> = emptyList(),
    val log: List<String> = emptyList()
)

/**
 * 실제 데이터 소스와 운행 기록 엔진이 에뮬레이터·Virtual Console 값으로 제대로 동작하는지 확인한다.
 * 모든 변화는 logcat(DriveLog/SdkCheck)에도 남긴다.
 */
class SdkCheckViewModel(private val container: AppContainer) : ViewModel() {

    private val vehicle = container.vehicle
    private val navi = container.navi
    private val _state = MutableStateFlow(SdkCheckState())
    val state: StateFlow<SdkCheckState> = _state.asStateFlow()

    init {
        watch("계기판", vehicle.odometerKm) { copy(odometer = it?.let { km -> "%.1f km".format(km) } ?: NO_VALUE) }
        watch("기어", vehicle.gear) { copy(gear = it.name) }
        watch("주차 브레이크(AAOS)", vehicle.parkingBrake) { copy(parkingBrake = it?.let { on -> if (on) "ON" else "OFF" } ?: NO_VALUE) }
        watch("주행 제한", vehicle.drivingRestricted) { copy(drivingRestricted = it) }
        watch("내비 도착", navi.arrivals) { this }
        watch("기록 엔진", container.recorder.state) { copy(recorderState = it.describe(), canClassify = it is TripState.Parked) }
        container.trips.observeTrips()
            .onEach { trips -> _state.update { it.copy(trips = trips.take(MAX_TRIPS).map { trip -> trip.describe() }) } }
            .launchIn(viewModelScope)
        refreshSettings()
        readVin()
        readEfficiency()
        readBattery()
        readCurrentPlace()
        readDestination()
    }

    fun readVin() = read("VIN", { vehicle.readVin() ?: NO_VALUE }) { copy(vin = it) }

    fun readEfficiency() =
        read("연비/전비", { vehicle.readEfficiency()?.let { "${it.value} (${it.type})" } ?: NO_VALUE }) { copy(efficiency = it) }

    fun readBattery() = read("배터리 잔량", { vehicle.readBatterySocPercent()?.let { "$it%" } ?: NO_VALUE }) { copy(battery = it) }

    fun readCurrentPlace() = read("현재 위치", { navi.currentPlace()?.describe() ?: NO_VALUE }) { copy(currentPlace = it) }

    fun readDestination() = read("목적지", { navi.destination()?.describe() ?: NO_VALUE }) { copy(destination = it) }

    /** debug 빌드에서 동의 화면을 거치지 않고 기록을 켜는 버튼 */
    fun startRecording() {
        container.consent.agree()
        refreshSettings()
        log("디버그: 동의로 처리하고 운행 기록 서비스를 시작했습니다")
    }

    fun toggleDemoTimeout() {
        val preferences = container.preferences
        preferences.parkedTimeoutMs =
            if (preferences.parkedTimeoutMs == DEMO_PARKED_TIMEOUT_MS) AppPreferences.DEFAULT_PARKED_TIMEOUT_MS else DEMO_PARKED_TIMEOUT_MS
        refreshSettings()
        log("정차 대기 시간: ${preferences.parkedTimeoutMs / 1000}초")
    }

    fun classify(purpose: TripPurpose) {
        container.recorder.classify(purpose)
        log("분류 선택: $purpose")
    }

    fun verifyChain() = read(
        "해시 체인 검증",
        { container.trips.verifyChain(container.vehicleIdentity.vehicleId()).toString() }
    ) { copy(chain = it) }

    private fun refreshSettings() = _state.update {
        it.copy(
            consentGiven = container.preferences.consentGiven,
            parkedTimeout = "${container.preferences.parkedTimeoutMs / 1000}초"
        )
    }

    private fun <T> watch(label: String, flow: Flow<T>, apply: SdkCheckState.(T) -> SdkCheckState) {
        flow.onEach { value ->
            _state.update { it.apply(value) }
            log("$label: $value")
        }.catch { error -> log("$label 오류: ${error.describe()}") }
            .launchIn(viewModelScope)
    }

    private fun read(label: String, block: suspend () -> String, apply: SdkCheckState.(String) -> SdkCheckState) {
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "실패: ${e.describe()}"
            }
            _state.update { it.apply(result) }
            log("$label → $result (${SystemClock.elapsedRealtime() - started}ms)")
        }
    }

    private fun log(message: String) {
        val time = LocalTime.now().format(TIME_FORMAT)
        _state.update { it.copy(log = (listOf("$time  $message") + it.log).take(MAX_LOG)) }
        Log.i(TAG, message)
    }

    private fun Throwable.describe() = "${javaClass.simpleName}: $message"

    private fun NaviPlace.describe() = listOfNotNull(name, address, "($latitude, $longitude)").joinToString(" ")

    private fun TripState.describe() = when (this) {
        TripState.Idle -> "대기"
        is TripState.Driving -> "주행 중 (${tripId.take(8)})"
        is TripState.Parked -> "정차 (${tripId.take(8)})"
    }

    private fun TripEntity.describe(): String {
        val time = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FORMAT)
        val distance = distanceKm()?.let { "%.1f km".format(it) } ?: "-"
        return "$time ${status.name} ${HashChain.km(startOdometerKm) ?: "-"}→${HashChain.km(endOdometerKm) ?: "-"} km ($distance) " +
            "${purpose.name} | 배터리 ${startSocPercent ?: "-"}→${endSocPercent ?: "-"}% | " +
            "${startAddress ?: "-"} → ${endAddress ?: "-"} | 비고 ${visitNote ?: "-"} | 해시 ${hash?.take(8) ?: "-"}"
    }

    companion object {
        private const val TAG = "DriveLog/SdkCheck"
        private const val NO_VALUE = "값 없음 (미지원 또는 응답 없음)"
        private const val MAX_LOG = 50
        private const val MAX_TRIPS = 5
        private const val DEMO_PARKED_TIMEOUT_MS = 30_000L
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DriveLogApp
                SdkCheckViewModel(app.container)
            }
        }
    }
}
