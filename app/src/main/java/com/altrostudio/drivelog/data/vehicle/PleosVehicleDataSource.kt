package com.altrostudio.drivelog.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 실제 차량 데이터.
 * - 계기판 거리, VIN, 연비·전비: Pleos Vehicle SDK
 * - 기어, 주차 브레이크, 주행 제한: AAOS Car API (Pleos Gear API는 deprecated이며 Google GEAR_SELECTION을 쓰라고 안내한다)
 */
class PleosVehicleDataSource(context: Context) : VehicleDataSource, AutoCloseable {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val vehicle = Vehicle(appContext).also { it.initialize() }

    /** 차량 제어([PleosCabinControl])도 같은 Vehicle 서비스 연결을 쓴다 */
    val sdk: Vehicle get() = vehicle

    /** 차량 OS(AAOS)에서 실행 중인지 (아니면 차량 제어를 쓰지 않는다) */
    val isAutomotive: Boolean = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    // 차량 OS(AAOS)가 아니면 Car API가 없다
    private val car: Car? =
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) Car.createCar(appContext) else null
    private val propertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager?
    private val uxRestrictionsManager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager?

    override suspend fun readOdometerKm(): Float? {
        val odometer = vehicle.getOdometer()
        if (!awaitReadable { ok, fail -> odometer.checkCurrentDistanceDrivenCapabilities(ok, fail) }) return null
        return awaitVehicle<Float?> { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) }
    }

    override suspend fun readVin(): String? {
        val carInfo = vehicle.getCarInfo()
        if (!awaitReadable { ok, fail -> carInfo.checkVINInfoCapabilities(ok, fail) }) return null
        // 에뮬레이터는 VIN 대신 "invalid"를 돌려준다. 17자리 VIN 형식이 아니면 값이 없는 것으로 본다
        return awaitVehicle<String?> { ok, fail -> carInfo.getVINInfo(ok, fail) }
            ?.trim()
            ?.takeIf { VIN_FORMAT.matches(it) }
    }

    override suspend fun readEfficiency(): EnergyEfficiency? {
        val evBattery = vehicle.getEvBattery()
        if (awaitReadable { ok, fail -> evBattery.checkCurrentEvEfficiencyCapabilities(ok, fail) }) {
            return awaitVehicle<Float?> { ok, fail -> evBattery.getCurrentEvEfficiency(ok, fail) }
                ?.let { EnergyEfficiency(it, EnergyType.ELECTRIC) }
        }
        val fuel = vehicle.getFuel()
        if (awaitReadable { ok, fail -> fuel.checkCurrentFuelEconomyCapabilities(ok, fail) }) {
            return awaitVehicle<Float?> { ok, fail -> fuel.getCurrentFuelEconomy(ok, fail) }
                ?.let { EnergyEfficiency(it, EnergyType.FUEL) }
        }
        return null
    }

    // 배터리 에너지(kWh)는 AAOS EV_BATTERY_LEVEL에 있지만 실행 중 허용이 필요한 CAR_ENERGY 권한이라 쓰지 않는다
    override suspend fun readBatterySocPercent(): Int? {
        val evBattery = vehicle.getEvBattery()
        if (!awaitReadable { ok, fail -> evBattery.checkEvBatteryStateOfChargeCapabilities(ok, fail) }) return null
        return awaitVehicle<Int?> { ok, fail -> evBattery.getEvBatteryStateOfCharge(ok, fail) }
    }

    override val odometerKm: Flow<Float?> = callbackFlow {
        val odometer = vehicle.getOdometer()
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                trySend(value)
            }

            override fun onFailed(e: Exception) {
                close(e)
            }
        }
        odometer.registerDistanceDriven(listener)
        // 등록만으로 현재 값이 오는지 확실하지 않아 한 번 직접 읽는다
        send(readOdometerKm())
        awaitClose { runCatching { odometer.unregisterDistanceDriven(listener) } }
    }.distinctUntilChanged().shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override val gear: Flow<GearPosition> =
        carProperty(VehiclePropertyIds.GEAR_SELECTION) { (it as? Int).toGearPosition() }

    override val parkingBrake: Flow<Boolean?> =
        carProperty(VehiclePropertyIds.PARKING_BRAKE_ON) { it as? Boolean }

    override val drivingRestricted: Flow<Boolean> = uxRestrictionsManager?.let { manager ->
        callbackFlow {
            trySend(manager.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false)
            manager.registerListener { restrictions -> trySend(restrictions.isRequiresDistractionOptimization) }
            awaitClose { runCatching { manager.unregisterListener() } }
        }.distinctUntilChanged().shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
    } ?: flowOf(false)

    override fun close() {
        scope.cancel()
        vehicle.release()
        car?.disconnect()
    }

    /** 변화가 있을 때만 값이 오는 AAOS 속성을 구독한다. 등록하면 현재 값이 먼저 한 번 온다 */
    @Suppress("DEPRECATION") // subscribePropertyEvents는 Android 15부터. 이 차량은 Android 14다
    private fun <T> carProperty(propertyId: Int, convert: (Any?) -> T): Flow<T> {
        val manager = propertyManager ?: return emptyFlow()
        return callbackFlow {
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    trySend(convert(value.value))
                }

                override fun onErrorEvent(propId: Int, areaId: Int) = Unit
            }
            if (!manager.registerCallback(callback, propertyId, CarPropertyManager.SENSOR_RATE_ONCHANGE)) {
                close(IllegalStateException("${VehiclePropertyIds.toString(propertyId)} 구독 실패"))
            }
            awaitClose { runCatching { manager.unregisterCallback(callback, propertyId) } }
        }.distinctUntilChanged().shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
    }

    /** onComplete/onFailed 콜백 API를 suspend 함수로 바꾼다. 실패하면 예외, 응답이 없으면 null */
    private suspend fun <T> awaitVehicle(
        call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit
    ): T? = withTimeoutOrNull(CALL_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            call(
                { value -> if (continuation.isActive) continuation.resume(value) },
                { error -> if (continuation.isActive) continuation.resumeWithException(error) }
            )
        }
    }

    /** checkXXXCapabilities로 이 차량이 값을 읽을 수 있는지 확인한다 */
    private suspend fun awaitReadable(
        check: (onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit) -> Unit
    ): Boolean = awaitVehicle(check)?.accessType.let { it == AccessType.READ || it == AccessType.READ_WRITE }

    private fun Int?.toGearPosition(): GearPosition = when (this) {
        VehicleGear.GEAR_PARK -> GearPosition.PARK
        VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
        else -> GearPosition.DRIVE // D와 수동 1~9단
    }

    private companion object {
        const val CALL_TIMEOUT_MS = 3_000L
        const val STOP_TIMEOUT_MS = 5_000L
        val VIN_FORMAT = Regex("^[A-HJ-NPR-Z0-9]{17}$")
    }
}
