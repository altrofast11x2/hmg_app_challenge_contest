package com.altrostudio.drivelog.debug

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.app.Activity
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * debug 빌드 전용: 이 앱이 Vehicle SDK 제어 API로 차량 상태를 실제로 바꿀 수 있는지 확인한다.
 * `adb shell am start -n com.altrostudio.drivelog/.debug.ControlProbeActivity --es only <항목>`으로 실행하고
 * logcat 태그 `DLProbe`로 결과를 본다. 바꾼 값은 끝날 때 원래대로 돌려놓는다.
 */
class ControlProbeActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var vehicle: Vehicle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = Vehicle(applicationContext).also { it.initialize() }
        val only = intent.getStringExtra("only")
        scope.launch {
            delay(1_500) // 서비스 연결
            runCatching {
                if (only == null || only == "ambient") ambient()
                if (only == null || only == "hvac") hvac()
                if (only == null || only == "seatheat") seatHeat()
                if (only == null || only == "seat") seat()
                if (only == null || only == "window") window()
                if (only == null || only == "door") door()
                if (only == null || only == "cabin") cabin()
                if (only == null || only == "occupancy") occupancy()
                if (only == "seatread") seatRead()
                // 바꾸지 않고 읽기만 한다 (앱의 무드등 신호·하차 점검 확인용)
                if (only == "read") {
                    val cabin = com.altrostudio.drivelog.data.vehicle.PleosCabinControl(vehicle)
                    log("읽기 맞춤: ${cabin.readComfort().let { c -> "온도 ${c.tempC} 열선 ${c.seatHeat} 무드등 ${c.ambientColor?.let { "0x%06X".format(it) }}" }}")
                    log("읽기 무드등: ${cabin.readAmbient()?.let { a -> "색 ${a.color?.let { "0x%06X".format(it) }} 밝기 ${a.brightness} 켜짐 ${a.on}" }}")
                    log("읽기 차 안: ${cabin.readCabin()}")
                }
                // 값을 바꾼 채로 둔다 (시스템 화면 반영 확인용)
                if (only == "settemp") {
                    val value = intent.getFloatExtra("value", 22f)
                    log("공조 온도 고정: " + call<String> { ok, fail -> vehicle.getHVAC().setHvacTemperature(SeatArea(SeatValue.ROW_1_LEFT), value, TemperatureUnit.CELSIUS, { a, v -> ok("${a.values.toList()} $v") }, fail) })
                }
                if (only == "setseatheat") {
                    val value = intent.getIntExtra("value", 0)
                    log("운전석 열선 고정: " + call<String> { ok, fail -> vehicle.getSeat().setHvacSeatTemperature(SeatArea(SeatValue.ROW_1_LEFT), value, { a, v -> ok("${a.values.toList()} $v") }, fail) })
                }
            }.onFailure { log("중단: $it") }
            log("끝")
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        vehicle.release()
        super.onDestroy()
    }

    private suspend fun ambient() {
        val light = vehicle.getLight()
        val areas = call<List<SeatArea>> { ok, fail -> light.checkAmbientLightBrightnessColorCapabilities({ c, a -> log("무드등 색 지원: $c ${a.map { it.values.toList() }}"); ok(a) }, fail) }
        val driver = areas?.firstOrNull() ?: return
        log("무드등 밝기 지원: " + call<String> { ok, fail -> light.checkAmbientLightBrightnessCapabilities({ c, a -> ok("$c ${a.entries.joinToString { "${it.key.values.toList()}=${it.value}" }}") }, fail) })
        log("무드등 스위치 지원: " + call<String> { ok, fail -> light.checkAmbientLightSwitchCapabilities({ c, a -> ok("$c ${a.entries.joinToString { "${it.key.values.toList()}=${it.value}" }}") }, fail) })
        val before = call<Int?> { ok, fail -> light.getAmbientLightBrightnessColor(driver, ok, fail) }
        log("무드등 색 지금: ${before.hex()}")
        log("무드등 색 바꾸기 결과: " + call<String> { ok, fail -> light.setAmbientLightBrightnessColor(driver, 0xFFE0B060.toInt(), { a, v -> ok("$a ${v.hex()}") }, fail) })
        delay(1_500)
        log("무드등 색 다시 읽기: ${call<Int?> { ok, fail -> light.getAmbientLightBrightnessColor(driver, ok, fail) }.hex()}")
        val brightness = call<Int?> { ok, fail -> light.getAmbientLightBrightness(driver, ok, fail) }
        log("무드등 밝기 지금: $brightness")
        log("무드등 스위치 지금: " + call<LightSwitch?> { ok, fail -> light.getAmbientLightSwitch(driver, ok, fail) })
        if (before != null) {
            call<String> { ok, fail -> light.setAmbientLightBrightnessColor(driver, before, { a, v -> ok("$a ${v.hex()}") }, fail) }
            log("무드등 색 되돌림")
        }
    }

    private suspend fun hvac() {
        val hvac = vehicle.getHVAC()
        val areas = call<Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Float>>> { ok, fail -> hvac.checkHvacTemperatureCapabilities({ c: ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig, a: Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Float>> -> log("공조 온도 지원: $c ${a.entries.joinToString { "${it.key.values.toList()}=${it.value}" }}"); ok(a) }, fail) }
        val driver = areas?.keys?.firstOrNull() ?: return
        val before = call<Float?> { ok, fail -> hvac.getHvacTemperature(driver, TemperatureUnit.CELSIUS, ok, fail) }
        log("공조 설정 온도 지금: $before")
        log("공조 온도 바꾸기 결과: " + call<String> { ok, fail -> hvac.setHvacTemperature(driver, 22.5f, TemperatureUnit.CELSIUS, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(1_500)
        log("공조 설정 온도 다시 읽기: " + call<Float?> { ok, fail -> hvac.getHvacTemperature(driver, TemperatureUnit.CELSIUS, ok, fail) })
        if (before != null) call<String> { ok, fail -> hvac.setHvacTemperature(driver, before, TemperatureUnit.CELSIUS, { _, v -> ok("$v") }, fail) }
    }

    private suspend fun seatHeat() {
        val seat = vehicle.getSeat()
        val areas = call<Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>>> { ok, fail -> seat.checkSeatTemperatureCapabilities({ c, a -> log("시트 열선 지원: $c ${a.entries.joinToString { "${it.key.values.toList()}=${it.value}" }}"); ok(a) }, fail) }
        val driver = areas?.keys?.firstOrNull() ?: return
        val before = call<Int?> { ok, fail -> seat.getHvacSeatTemperature(driver, ok, fail) }
        log("시트 열선 지금: $before")
        log("시트 열선 바꾸기 결과: " + call<String> { ok, fail -> seat.setHvacSeatTemperature(driver, 2, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(1_500)
        log("시트 열선 다시 읽기: " + call<Int?> { ok, fail -> seat.getHvacSeatTemperature(driver, ok, fail) })
        if (before != null) call<String> { ok, fail -> seat.setHvacSeatTemperature(driver, before, { _, v -> ok("$v") }, fail) }
    }

    private suspend fun seat() {
        val seat = vehicle.getSeat()
        val areas = call<Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>>> { ok, fail -> seat.checkSeatForeAftPositionCapabilities({ c, a -> log("좌석 앞뒤 위치 지원: $c ${a.entries.joinToString { "${it.key.values.toList()}=${it.value}" }}"); ok(a) }, fail) }
        val driver = areas?.keys?.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: SeatArea(SeatValue.ROW_1_LEFT)
        log("좌석 앞뒤 이동 지원: " + call<String> { ok, fail -> seat.checkMoveSeatForeAftCapabilities({ c, a -> ok("$c ${a.map { it.values.toList() }}") }, fail) })
        log("좌석 앞뒤 위치 지금: " + call<Int?> { ok, fail -> seat.getCurrentSeatForeAftPosition(driver, ok, fail) })
        log("좌석 높이 지금: " + call<Int?> { ok, fail -> seat.getCurrentSeatHeightPosition(driver, ok, fail) })
        log("좌석 등받이 지금: " + call<Int?> { ok, fail -> seat.getCurrentSeatBackrestAnglePosition(driver, ok, fail) })
        log("좌석 앞으로 이동 시작: " + call<String> { ok, fail -> seat.moveSeatForeAft(driver, MoveControl.FORWARD, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        repeat(10) {
            delay(500)
            log("  이동 중 위치: " + call<Int?> { ok, fail -> seat.getCurrentSeatForeAftPosition(driver, ok, fail) })
        }
        log("좌석 멈춤: " + call<String> { ok, fail -> seat.moveSeatForeAft(driver, MoveControl.STOP, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(500)
        log("좌석 앞뒤 위치 멈춘 뒤: " + call<Int?> { ok, fail -> seat.getCurrentSeatForeAftPosition(driver, ok, fail) })
        log("좌석 뒤로 이동 시작: " + call<String> { ok, fail -> seat.moveSeatForeAft(driver, MoveControl.BACKWARD, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(3_000)
        call<String> { ok, fail -> seat.moveSeatForeAft(driver, MoveControl.STOP, { a, v -> ok("${a.values.toList()} $v") }, fail) }
        log("좌석 앞뒤 위치 되돌린 뒤: " + call<Int?> { ok, fail -> seat.getCurrentSeatForeAftPosition(driver, ok, fail) })
    }

    private suspend fun window() {
        val window = vehicle.getWindow()
        val areas = call<Map<WindowArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>>> { ok, fail -> window.checkWindowPositionCapabilities({ c, a -> log("창문 위치 지원: $c ${a.entries.joinToString { "${it.key.values.toList()}=${it.value}" }}"); ok(a) }, fail) }
        val front = areas?.keys?.firstOrNull { WindowValue.ROW_1_LEFT in it.values } ?: areas?.keys?.firstOrNull() ?: return
        val before = call<Int?> { ok, fail -> window.getCurrentWindowPosition(front, ok, fail) }
        log("운전석 창문 지금: $before")
        log("운전석 창문 바꾸기(열기 5): " + call<String> { ok, fail -> window.setWindowPosition(front, 5, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(2_000)
        log("운전석 창문 다시 읽기: " + call<Int?> { ok, fail -> window.getCurrentWindowPosition(front, ok, fail) })
        log("운전석 창문 닫기(0): " + call<String> { ok, fail -> window.setWindowPosition(front, before ?: 0, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(2_000)
        log("운전석 창문 닫은 뒤: " + call<Int?> { ok, fail -> window.getCurrentWindowPosition(front, ok, fail) })
    }

    private suspend fun door() {
        val door = vehicle.getDoor()
        val areas = call<List<DoorArea>> { ok, fail -> door.checkDoorLockCapabilities({ c, a -> log("문 잠금 지원: $c ${a.map { it.values.toList() }}"); ok(a) }, fail) }
        val front = areas?.firstOrNull { DoorValue.ROW_1_LEFT in it.values } ?: DoorArea(DoorValue.ROW_1_LEFT)
        log("문 위치 지원: " + call<String> { ok, fail -> door.checkDoorPositionCapabilities({ c, a -> ok("$c ${a.entries.joinToString { "${it.key.values.toList()}=${it.value}" }}") }, fail) })
        log("운전석 문 위치 지금: " + call<Int?> { ok, fail -> door.getCurrentDoorPosition(front, ok, fail) })
        log("운전석 문 잠금 지금: " + call<Boolean?> { ok, fail -> door.getDoorLockState(front, ok, fail) })
        log("문 열림(잠금 해제) 결과: " + call<String> { ok, fail -> door.setDoorLock(front, false, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(1_500)
        log("운전석 문 잠금 다시 읽기: " + call<Boolean?> { ok, fail -> door.getDoorLockState(front, ok, fail) })
        log("문 잠금 결과: " + call<String> { ok, fail -> door.setDoorLock(front, true, { a, v -> ok("${a.values.toList()} $v") }, fail) })
        delay(1_500)
        log("운전석 문 잠금 다시 읽기: " + call<Boolean?> { ok, fail -> door.getDoorLockState(front, ok, fail) })
    }

    private suspend fun cabin() {
        val light = vehicle.getLight()
        val before = call<LightSwitch?> { ok, fail -> light.getCabinLightSwitch(ok, fail) }
        log("실내등 스위치 지금: $before")
        log("실내등 켜기 결과: " + call<String> { ok, fail -> light.setCabinLightSwitch(LightSwitch.ON, { v -> ok("$v") }, fail) })
        delay(1_500)
        log("실내등 다시 읽기: " + call<LightSwitch?> { ok, fail -> light.getCabinLightSwitch(ok, fail) })
        call<String> { ok, fail -> light.setCabinLightSwitch(before ?: LightSwitch.OFF, { v -> ok("$v") }, fail) }
        log("실내등 되돌림")
    }

    /** Virtual Console에서 바꾼 좌석 위치가 조회·리스너로 들어오는지 본다 (8초 동안 리스너 유지) */
    private suspend fun seatRead() {
        val seat = vehicle.getSeat()
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        log("조회 앞뒤 ${call<Int?> { ok, fail -> seat.getCurrentSeatForeAftPosition(driver, ok, fail) }} 높이 ${call<Int?> { ok, fail -> seat.getCurrentSeatHeightPosition(driver, ok, fail) }} 등받이 ${call<Int?> { ok, fail -> seat.getCurrentSeatBackrestAnglePosition(driver, ok, fail) }} 착석 ${call<Boolean?> { ok, fail -> seat.getCurrentSeatOccupancy(driver, ok, fail) }}")
        val listener = object : ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener {
            override fun onSeatForeAftPositionUpdated(position: Int?) = log("  리스너 앞뒤 위치: $position")
            override fun onFailed(e: Exception) = log("  리스너 실패: $e")
        }
        seat.registerSeatForeAftPosition(driver, listener)
        delay(8_000)
        runCatching { seat.unregisterSeatForeAftPosition(driver, listener) }
        log("조회(8초 뒤) 앞뒤 ${call<Int?> { ok, fail -> seat.getCurrentSeatForeAftPosition(driver, ok, fail) }}")
    }

    private suspend fun occupancy() {
        val seat = vehicle.getSeat()
        log("착석 감지 지원: " + call<String> { ok, fail -> seat.checkSeatOccupancyCapabilities({ c, a -> ok("$c ${a.map { it.values.toList() }}") }, fail) })
        log("운전석 착석: " + call<Boolean?> { ok, fail -> seat.getCurrentSeatOccupancy(SeatArea(SeatValue.ROW_1_LEFT), ok, fail) })
    }

    /** 콜백 API를 기다린다. 실패는 문자열로 남기고 null을 돌려준다 */
    private suspend fun <T> call(block: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(4_000) {
            suspendCancellableCoroutine { continuation ->
                runCatching {
                    block(
                        { value -> if (continuation.isActive) continuation.resume(value) },
                        { error -> log("  실패: ${error.javaClass.simpleName}: ${error.message}"); if (continuation.isActive) continuation.resume(null) }
                    )
                }.onFailure { error ->
                    log("  예외: ${error.javaClass.simpleName}: ${error.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    private fun Int?.hex(): String = this?.let { "0x%08X".format(it) } ?: "null"

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "DLProbe"
    }
}
