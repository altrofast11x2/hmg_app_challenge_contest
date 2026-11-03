package com.altrostudio.drivelog.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pleos Vehicle SDK로 차 안을 제어한다 (2026-11-03 Pleos Connect 에뮬레이터 + Virtual Console에서 확인한 방식).
 * - 영역은 반드시 check*Capabilities가 준 것을 쓴다. 예: 무드등은 좌석 다섯 개를 묶은 영역 하나이고, 운전석만 따로 주면 거부된다
 * - 바꾼 뒤 다시 읽어 같은 값일 때만 성공으로 본다
 * - 운전석은 1열 왼쪽(ROW_1_LEFT)이다 (국내 왼쪽 운전석 차량)
 */
class PleosCabinControl(private val vehicle: Vehicle) : CabinControl {

    private val mutex = Mutex()

    override suspend fun readComfort(): Comfort = mutex.withLock {
        Comfort(
            tempC = safely { readTemp() },
            seatHeat = safely { readSeatHeat() },
            ambientColor = safely { readAmbientColor() }
        )
    }

    override suspend fun applyComfort(target: Comfort): Comfort = mutex.withLock {
        Comfort(
            tempC = target.tempC?.let { value -> safely { applyTemp(value) } },
            seatHeat = target.seatHeat?.let { value -> safely { applySeatHeat(value) } },
            ambientColor = target.ambientColor?.let { value -> safely { applyAmbientColor(value) } }
        )
    }

    override suspend fun readAmbient(): AmbientLight? = mutex.withLock {
        val area = safely { ambientArea() } ?: return@withLock null
        val light = vehicle.getLight()
        AmbientLight(
            color = safely { readAmbientColor() },
            brightness = safely { call<Int?> { ok, fail -> light.getAmbientLightBrightness(area, ok, fail) } },
            on = safely { call<LightSwitch?> { ok, fail -> light.getAmbientLightSwitch(area, ok, fail) } }
                ?.let { it != LightSwitch.OFF }
        )
    }

    override suspend fun showAmbient(color: Int, minBrightness: Int): Boolean = mutex.withLock {
        val area = safely { ambientArea() } ?: return@withLock false
        val light = vehicle.getLight()
        safely {
            val switch = call<LightSwitch?> { ok, fail -> light.getAmbientLightSwitch(area, ok, fail) }
            if (switch == LightSwitch.OFF) {
                call<Unit> { ok, fail -> light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> ok(Unit) }, fail) }
            }
            val brightness = call<Int?> { ok, fail -> light.getAmbientLightBrightness(area, ok, fail) }
            if (brightness != null && brightness < minBrightness) {
                call<Unit> { ok, fail -> light.setAmbientLightBrightness(area, minBrightness, { _, _ -> ok(Unit) }, fail) }
            }
        }
        safely { applyAmbientColor(color) } != null
    }

    override suspend fun restoreAmbient(saved: AmbientLight): Boolean = mutex.withLock {
        val area = safely { ambientArea() } ?: return@withLock false
        val light = vehicle.getLight()
        val colorRestored = saved.color?.let { safely { applyAmbientColor(it) } != null } ?: true
        saved.brightness?.let { value ->
            safely { call<Unit> { ok, fail -> light.setAmbientLightBrightness(area, value, { _, _ -> ok(Unit) }, fail) } }
        }
        if (saved.on == false) {
            safely { call<Unit> { ok, fail -> light.setAmbientLightSwitch(area, LightSwitch.OFF, { _, _ -> ok(Unit) }, fail) } }
        }
        colorRestored
    }

    override suspend fun readCabin(): CabinState = mutex.withLock {
        CabinState(
            openWindows = safely { openWindows() },
            doorsLocked = safely {
                val area = doorArea() ?: return@safely null
                call<Boolean?> { ok, fail -> vehicle.getDoor().getDoorLockState(area, ok, fail) }
            },
            cabinLightOn = safely {
                if (!readable { ok, fail -> vehicle.getLight().checkCabinLightSwitchCapabilities({ c, _ -> ok(c) }, fail) }) {
                    return@safely null
                }
                call<LightSwitch?> { ok, fail -> vehicle.getLight().getCabinLightSwitch(ok, fail) }?.let { it == LightSwitch.ON }
            }
        )
    }

    override suspend fun closeWindows(): Boolean = mutex.withLock {
        safely {
            val window = vehicle.getWindow()
            val areas = windowAreas()
            areas.forEach { (area, bounds) ->
                val position = call<Int?> { ok, fail -> window.getCurrentWindowPosition(area, ok, fail) }
                if (position != null && position > bounds.minValue) {
                    call<Unit> { ok, fail -> window.setWindowPosition(area, bounds.minValue, { _, _ -> ok(Unit) }, fail) }
                }
            }
            confirm { openWindows().isEmpty() }
        } ?: false
    }

    override suspend fun turnOffCabinLight(): Boolean = mutex.withLock {
        safely {
            val light = vehicle.getLight()
            call<Unit> { ok, fail -> light.setCabinLightSwitch(LightSwitch.OFF, { ok(Unit) }, fail) }
            confirm { call<LightSwitch?> { ok, fail -> light.getCabinLightSwitch(ok, fail) } == LightSwitch.OFF }
        } ?: false
    }

    override suspend fun lockDoors(): Boolean = mutex.withLock {
        safely {
            val door = vehicle.getDoor()
            val area = doorArea() ?: return@safely false
            call<Unit> { ok, fail -> door.setDoorLock(area, true, { _, _ -> ok(Unit) }, fail) }
            confirm { call<Boolean?> { ok, fail -> door.getDoorLockState(area, ok, fail) } == true }
        } ?: false
    }

    // ─── 항목별 ─────────────────────────────────────────────────────────

    private var tempAreaCache: Pair<SeatArea, AreaBounds<Float>>? = null
    private var seatHeatAreaCache: Pair<SeatArea, AreaBounds<Int>>? = null
    private var ambientAreaCache: SeatArea? = null
    private var windowAreasCache: Map<WindowArea, AreaBounds<Int>>? = null
    private var doorAreaCache: DoorArea? = null

    private suspend fun tempArea(): Pair<SeatArea, AreaBounds<Float>>? = tempAreaCache ?: run {
        val hvac = vehicle.getHVAC()
        call<Pair<SeatArea, AreaBounds<Float>>?> { ok, fail ->
            hvac.checkHvacTemperatureCapabilities(
                { config: CapabilityConfig, areas: Map<SeatArea, AreaBounds<Float>> ->
                    ok(if (config.writable) areas.entries.firstOrNull { it.key.isDriver }?.toPair() else null)
                },
                fail
            )
        }?.also { tempAreaCache = it }
    }

    private suspend fun readTemp(): Float? {
        val (area, _) = tempArea() ?: return null
        return call<Float?> { ok, fail -> vehicle.getHVAC().getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) }
    }

    private suspend fun applyTemp(value: Float): Float? {
        val (area, bounds) = tempArea() ?: return null
        val target = ((value.coerceIn(bounds.minValue, bounds.maxValue)) * 2).roundToInt() / 2f // 0.5℃ 단위
        call<Unit> { ok, fail ->
            vehicle.getHVAC().setHvacTemperature(area, target, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, fail)
        }
        return target.takeIf { confirm { readTemp()?.let { abs(it - target) < 0.05f } == true } }
    }

    private suspend fun seatHeatArea(): Pair<SeatArea, AreaBounds<Int>>? = seatHeatAreaCache ?: run {
        val seat = vehicle.getSeat()
        call<Pair<SeatArea, AreaBounds<Int>>?> { ok, fail ->
            seat.checkSeatTemperatureCapabilities(
                { config: CapabilityConfig, areas: Map<SeatArea, AreaBounds<Int>> ->
                    ok(if (config.writable) areas.entries.firstOrNull { it.key.isDriver }?.toPair() else null)
                },
                fail
            )
        }?.also { seatHeatAreaCache = it }
    }

    private suspend fun readSeatHeat(): Int? {
        val (area, _) = seatHeatArea() ?: return null
        return call<Int?> { ok, fail -> vehicle.getSeat().getHvacSeatTemperature(area, ok, fail) }
    }

    private suspend fun applySeatHeat(value: Int): Int? {
        val (area, bounds) = seatHeatArea() ?: return null
        val target = value.coerceIn(bounds.minValue, bounds.maxValue)
        call<Unit> { ok, fail -> vehicle.getSeat().setHvacSeatTemperature(area, target, { _, _ -> ok(Unit) }, fail) }
        return target.takeIf { confirm { readSeatHeat() == target } }
    }

    private suspend fun ambientArea(): SeatArea? = ambientAreaCache ?: run {
        val light = vehicle.getLight()
        call<SeatArea?> { ok, fail ->
            light.checkAmbientLightBrightnessColorCapabilities(
                { config: CapabilityConfig, areas: List<SeatArea> -> ok(if (config.writable) areas.firstOrNull() else null) },
                fail
            )
        }?.also { ambientAreaCache = it }
    }

    /** 0xRRGGBB. 차는 알파를 0으로 돌려준다 */
    private suspend fun readAmbientColor(): Int? {
        val area = ambientArea() ?: return null
        return call<Int?> { ok, fail -> vehicle.getLight().getAmbientLightBrightnessColor(area, ok, fail) }?.and(RGB_MASK)
    }

    private suspend fun applyAmbientColor(rgb: Int): Int? {
        val area = ambientArea() ?: return null
        val target = rgb and RGB_MASK
        call<Unit> { ok, fail ->
            vehicle.getLight().setAmbientLightBrightnessColor(area, OPAQUE or target, { _, _ -> ok(Unit) }, fail)
        }
        return target.takeIf { confirm { readAmbientColor() == target } }
    }

    private suspend fun windowAreas(): Map<WindowArea, AreaBounds<Int>> = windowAreasCache ?: run {
        val window = vehicle.getWindow()
        (call<Map<WindowArea, AreaBounds<Int>>?> { ok, fail ->
            window.checkWindowPositionCapabilities(
                { config: CapabilityConfig, areas: Map<WindowArea, AreaBounds<Int>> ->
                    ok(if (config.writable) areas.filterKeys { it.cabinWindow != null } else emptyMap())
                },
                fail
            )
        } ?: emptyMap()).also { if (it.isNotEmpty()) windowAreasCache = it }
    }

    private suspend fun openWindows(): Set<CabinWindow> {
        val window = vehicle.getWindow()
        return windowAreas().mapNotNull { (area, bounds) ->
            val position = call<Int?> { ok, fail -> window.getCurrentWindowPosition(area, ok, fail) }
            area.cabinWindow?.takeIf { position != null && position > bounds.minValue }
        }.toSet()
    }

    /** 운전석 문이 들어 있는 잠금 영역 (보닛 영역은 따로 있다) */
    private suspend fun doorArea(): DoorArea? = doorAreaCache ?: run {
        val door = vehicle.getDoor()
        call<DoorArea?> { ok, fail ->
            door.checkDoorLockCapabilities(
                { config: CapabilityConfig, areas: List<DoorArea> ->
                    ok(if (config.writable) areas.firstOrNull { DoorValue.ROW_1_LEFT in it.values } else null)
                },
                fail
            )
        }?.also { doorAreaCache = it }
    }

    // ─── 공통 ─────────────────────────────────────────────────────────

    private val SeatArea.isDriver: Boolean get() = SeatValue.ROW_1_LEFT in values

    private val WindowArea.cabinWindow: CabinWindow?
        get() = values.singleOrNull()?.let {
            when (it) {
                WindowValue.ROW_1_LEFT -> CabinWindow.FRONT_LEFT
                WindowValue.ROW_1_RIGHT -> CabinWindow.FRONT_RIGHT
                WindowValue.ROW_2_LEFT -> CabinWindow.REAR_LEFT
                WindowValue.ROW_2_RIGHT -> CabinWindow.REAR_RIGHT
                else -> null
            }
        }

    private val CapabilityConfig.writable: Boolean
        get() = accessType == AccessType.WRITE || accessType == AccessType.READ_WRITE

    private suspend fun readable(check: (onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit) -> Unit): Boolean =
        call(check)?.accessType.let { it == AccessType.READ || it == AccessType.READ_WRITE }

    /** 바꾼 값이 다시 읽힐 때까지 잠깐씩 기다린다 */
    private suspend fun confirm(check: suspend () -> Boolean): Boolean {
        repeat(CONFIRM_TRIES) { attempt ->
            if (check()) return true
            if (attempt < CONFIRM_TRIES - 1) delay(CONFIRM_INTERVAL_MS)
        }
        return false
    }

    /** onComplete/onFailed 콜백 API를 기다린다. 실패하면 예외, 응답이 없으면 null */
    private suspend fun <T> call(block: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                block(
                    { value -> if (continuation.isActive) continuation.resume(value) },
                    { error -> if (continuation.isActive) continuation.resumeWith(Result.failure(error)) }
                )
            }
        }

    /** 지원하지 않는 차량·영역은 흔하므로 실패는 한 줄로 남기고 null */
    private suspend fun <T> safely(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "차량 제어 실패: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private companion object {
        const val TAG = "DriveLog/Cabin"
        const val CALL_TIMEOUT_MS = 3_000L
        const val CONFIRM_TRIES = 6
        const val CONFIRM_INTERVAL_MS = 250L
        const val RGB_MASK = 0xFFFFFF
        const val OPAQUE = 0xFF shl 24
    }
}
