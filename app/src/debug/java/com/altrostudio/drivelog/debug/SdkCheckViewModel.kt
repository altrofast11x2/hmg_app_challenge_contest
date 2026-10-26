package com.altrostudio.drivelog.debug

import ai.pleos.playground.vehicle.Vehicle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.altrostudio.drivelog.data.navi.NaviPlace
import com.altrostudio.drivelog.data.navi.PleosNaviDataSource
import com.altrostudio.drivelog.data.vehicle.PleosVehicleDataSource
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SdkCheckState(
    val odometer: String = "-",
    val gear: String = "-",
    val parkingBrake: String = "-",
    val drivingRestricted: Boolean = false,
    val vin: String = "-",
    val efficiency: String = "-",
    val energyProbe: String = "-",
    val currentPlace: String = "-",
    val destination: String = "-",
    val log: List<String> = emptyList()
)

/** 실제 데이터 소스가 에뮬레이터·Virtual Console 값을 제대로 읽는지 확인한다. 모든 변화는 logcat(DriveLog/SdkCheck)에도 남긴다 */
class SdkCheckViewModel(
    private val vehicle: PleosVehicleDataSource,
    private val navi: PleosNaviDataSource,
    private val rawVehicle: Vehicle
) : ViewModel() {

    private val _state = MutableStateFlow(SdkCheckState())
    val state: StateFlow<SdkCheckState> = _state.asStateFlow()

    init {
        watch("계기판", vehicle.odometerKm) { copy(odometer = it?.let { km -> "%.1f km".format(km) } ?: NO_VALUE) }
        watch("기어", vehicle.gear) { copy(gear = it.name) }
        watch("주차 브레이크(AAOS)", vehicle.parkingBrake) { copy(parkingBrake = it?.let { on -> if (on) "ON" else "OFF" } ?: NO_VALUE) }
        watch("주행 제한", vehicle.drivingRestricted) { copy(drivingRestricted = it) }
        watch("내비 도착", navi.arrivals) { this }
        readVin()
        readEfficiency()
        probeEnergy()
        readCurrentPlace()
        readDestination()
    }

    fun readVin() = read("VIN", { vehicle.readVin() ?: NO_VALUE }) { copy(vin = it) }

    fun readEfficiency() =
        read("연비/전비", { vehicle.readEfficiency()?.let { "${it.value} (${it.type})" } ?: NO_VALUE }) { copy(efficiency = it) }

    fun probeEnergy() = read("에너지 실험", ::probeEnergyValues) { copy(energyProbe = it) }

    fun readCurrentPlace() = read("현재 위치", { navi.currentPlace()?.describe() ?: NO_VALUE }) { copy(currentPlace = it) }

    fun readDestination() = read("목적지", { navi.destination()?.describe() ?: NO_VALUE }) { copy(destination = it) }

    override fun onCleared() {
        vehicle.close()
        navi.close()
        rawVehicle.release()
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

    // 실험: 이 차량(에뮬레이터)에서 읽을 수 있는 연비·전비·잔량 값을 찾는다
    private suspend fun probeEnergyValues(): String {
        val ev = rawVehicle.getEvBattery()
        val fuel = rawVehicle.getFuel()
        val results = listOf(
            "EV 순간" to rawCall<Float?> { ok, fail -> ev.getInstantaneousEvEfficiency(ok, fail) },
            "EV 충전후" to rawCall<Float?> { ok, fail -> ev.getSinceChargeEvEfficiency(ok, fail) },
            "EV 누적" to rawCall<Float?> { ok, fail -> ev.getCumulativeEvEfficiency(ok, fail) },
            "EV 잔량%" to rawCall<Int?> { ok, fail -> ev.getEvBatteryStateOfCharge(ok, fail) },
            "연료 순간" to rawCall<Float?> { ok, fail -> fuel.getInstantaneousFuelEconomy(ok, fail) },
            "연료 누적" to rawCall<Float?> { ok, fail -> fuel.getCumulativeFuelEconomy(ok, fail) },
            "연료 잔량%" to rawCall<Int?> { ok, fail -> fuel.getFuelPercent(ok, fail) }
        )
        return results.joinToString(" / ") { (name, value) -> "$name=$value" }
    }

    private suspend fun <T> rawCall(call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit): String =
        try {
            withTimeoutOrNull(RAW_TIMEOUT_MS) {
                suspendCancellableCoroutine<T> { continuation ->
                    call(
                        { if (continuation.isActive) continuation.resume(it) },
                        { if (continuation.isActive) continuation.resumeWithException(it) }
                    )
                }
            }.toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "실패(${e.message})"
        }

    private fun log(message: String) {
        val time = LocalTime.now().format(TIME_FORMAT)
        _state.update { it.copy(log = (listOf("$time  $message") + it.log).take(MAX_LOG)) }
        Log.i(TAG, message)
    }

    private fun Throwable.describe() = "${javaClass.simpleName}: $message"

    private fun NaviPlace.describe() = listOfNotNull(name, address, "($latitude, $longitude)").joinToString(" ")

    companion object {
        private const val TAG = "DriveLog/SdkCheck"
        private const val NO_VALUE = "값 없음 (미지원 또는 응답 없음)"
        private const val MAX_LOG = 50
        private const val RAW_TIMEOUT_MS = 3_000L
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                SdkCheckViewModel(
                    PleosVehicleDataSource(app),
                    PleosNaviDataSource(app),
                    Vehicle(app).also { it.initialize() }
                )
            }
        }
    }
}
