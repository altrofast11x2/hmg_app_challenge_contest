package com.hmgchallenge.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 에뮬레이터에서 Vehicle SDK·AAOS Car API·ADAS 서비스로 무엇을 읽고 제어할 수 있는지 확인하는 디버그 전용 화면.
 * 실행: adb shell am start -n com.hmgchallenge.app/.DiagnosticsActivity, 결과는 logcat 태그 PleosDiag.
 */
class DiagnosticsActivity : ComponentActivity() {

    private val vehicle by lazy { Vehicle(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var adasBound = false
    private val lines = mutableStateListOf<String>()

    private val adasConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            log(ADAS, "바인딩", "성공 (인터페이스: ${binder.interfaceDescriptor})")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            log(ADAS, "바인딩", "연결 끊김")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = ::runAll) { Text("다시 실행", fontSize = 20.sp) }
                        LazyColumn {
                            items(lines) { Text(it, fontSize = 14.sp, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
            }
        }
        runCatching { vehicle.initialize() }.onFailure { log(VEHICLE, "initialize", "FAIL ${it.message}") }
        runAll()
    }

    private fun runAll() {
        lines.clear()
        checkPermissions()
        readVehicleState()
        controlVehicle()
        readCarProperties()
        bindAdas()
    }

    private fun checkPermissions() {
        val requested = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        requested.forEach { perm ->
            val granted = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            log(PERM, perm.substringAfterLast('.'), if (granted) "granted" else "DENIED")
        }
    }

    // ① 조회: 아이디어에 쓸 만한 차량 상태
    private fun readVehicleState() {
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        vehicle.getGear().getCurrentGearSelectionState(ok(VEHICLE, "기어"), fail(VEHICLE, "기어"))
        vehicle.getBrake().getCurrentBrakePedalStatus(ok(VEHICLE, "브레이크 페달"), fail(VEHICLE, "브레이크 페달"))
        vehicle.getTurnSignal().getCurrentTurnSignalState(ok(VEHICLE, "방향지시등"), fail(VEHICLE, "방향지시등"))
        vehicle.getDrivingMode().getCurrentDrivingModeState(ok(VEHICLE, "주행 모드"), fail(VEHICLE, "주행 모드"))
        vehicle.getEvBattery().getEvBatteryLevel(ok(VEHICLE, "EV 배터리"), fail(VEHICLE, "EV 배터리"))
        vehicle.getTire().getTirePressure(WheelArea(WheelValue.LEFT_FRONT), ok(VEHICLE, "타이어 공기압(좌전)"), fail(VEHICLE, "타이어 공기압(좌전)"))
        vehicle.getSafety().getTelltalesInfo(ok(VEHICLE, "경고등"), fail(VEHICLE, "경고등"))
        vehicle.getWiper().getCurrentWiperSwitch(WindowArea(WindowValue.FRONT_WINDSHIELD), ok(VEHICLE, "와이퍼"), fail(VEHICLE, "와이퍼"))
        vehicle.getSeat().getCurrentSeatBackrestAnglePosition(driver, ok(VEHICLE, "운전석 등받이 각도"), fail(VEHICLE, "운전석 등받이 각도"))
        vehicle.getSeat().getCurrentSeatOccupancy(SeatArea(SeatValue.ROW_2_LEFT), ok(VEHICLE, "2열 좌석 착석"), fail(VEHICLE, "2열 좌석 착석"))
    }

    // ② 제어: 지원 영역 확인 → 현재 값 조회 → 다른 값으로 변경 → 3초 뒤 다시 조회 (액추에이터 동작 지연 고려)
    private fun controlVehicle() {
        val door = vehicle.getDoor()
        door.checkDoorLockCapabilities({ areas: List<DoorArea> ->
            log(CONTROL, "도어 잠금 지원 영역", areas.joinToString { it.values.contentToString() })
            val area = areas.firstOrNull() ?: return@checkDoorLockCapabilities
            door.getDoorLockState(area, { locked ->
                log(CONTROL, "도어 잠금", "변경 전 $locked")
                door.setDoorLock(area, locked != true, { _, value ->
                    log(CONTROL, "도어 잠금", "set 응답 $value")
                    later { door.getDoorLockState(area, ok(CONTROL, "도어 잠금 3초 후"), fail(CONTROL, "도어 잠금 3초 후")) }
                }, fail(CONTROL, "도어 잠금 set"))
            }, fail(CONTROL, "도어 잠금 get"))
        }, fail(CONTROL, "도어 잠금 지원 영역"))

        val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
        val window = vehicle.getWindow()
        window.checkWindowPositionCapabilities({ bounds: Map<WindowArea, AreaBounds<Int>> ->
            val range = bounds[driverWindow]
            log(CONTROL, "창문 범위", "$range")
            val target = range?.let { (it.minValue + it.maxValue) / 2 } ?: 5
            window.getCurrentWindowPosition(driverWindow, { before ->
                log(CONTROL, "운전석 창문", "변경 전 $before")
                window.setWindowPosition(driverWindow, if (before == target) target + 1 else target, { _, value ->
                    log(CONTROL, "운전석 창문", "set 응답 $value")
                    later { window.getCurrentWindowPosition(driverWindow, ok(CONTROL, "운전석 창문 3초 후"), fail(CONTROL, "운전석 창문 3초 후")) }
                }, fail(CONTROL, "운전석 창문 set"))
            }, fail(CONTROL, "운전석 창문 get"))
        }, fail(CONTROL, "창문 범위"))

        // 온도 설정은 공조 전원이 켜져 있어야 가능할 수 있어 전원부터 확인
        val hvac = vehicle.getHVAC()
        hvac.checkHvacPowerCapabilities({ powerAreas: List<SeatArea> ->
            log(CONTROL, "공조 전원 지원 영역", powerAreas.joinToString { it.values.contentToString() })
            val area = powerAreas.firstOrNull()
            if (area == null) {
                testHvacTemperature()
                return@checkHvacPowerCapabilities
            }
            hvac.getHvacPowerState(area, { on ->
                log(CONTROL, "공조 전원", "현재 $on")
                if (on == true) testHvacTemperature()
                else hvac.setHvacPower(area, true, { _, value ->
                    log(CONTROL, "공조 전원", "set 응답 $value")
                    later { testHvacTemperature() }
                }, fail(CONTROL, "공조 전원 set"))
            }, fail(CONTROL, "공조 전원 get"))
        }, fail(CONTROL, "공조 전원 지원 영역"))

        val light = vehicle.getLight()
        light.checkAmbientLightSwitchCapabilities({ _, switches ->
            log(CONTROL, "앰비언트 조명 지원 영역", switches.entries.joinToString { "${it.key.values.contentToString()}=${it.value}" })
            val area = switches.keys.firstOrNull() ?: return@checkAmbientLightSwitchCapabilities
            light.getAmbientLightSwitch(area, { before ->
                log(CONTROL, "앰비언트 조명", "변경 전 $before")
                light.setAmbientLightSwitch(area, if (before == LightSwitch.ON) LightSwitch.OFF else LightSwitch.ON, { _, value ->
                    log(CONTROL, "앰비언트 조명", "set 응답 $value")
                    later { light.getAmbientLightSwitch(area, ok(CONTROL, "앰비언트 조명 3초 후"), fail(CONTROL, "앰비언트 조명 3초 후")) }
                }, fail(CONTROL, "앰비언트 조명 set"))
            }, fail(CONTROL, "앰비언트 조명 get"))
        }, fail(CONTROL, "앰비언트 조명 지원 영역"))

        val seat = vehicle.getSeat()
        seat.checkSeatTemperatureCapabilities({ bounds: Map<SeatArea, AreaBounds<Int>> ->
            log(CONTROL, "시트 열선 지원 영역", bounds.entries.joinToString { "${it.key.values.contentToString()}=${it.value}" })
            val (area, range) = bounds.entries.firstOrNull()?.toPair() ?: return@checkSeatTemperatureCapabilities
            seat.getHvacSeatTemperature(area, { before ->
                log(CONTROL, "시트 열선", "변경 전 $before")
                seat.setHvacSeatTemperature(area, if (before == range.maxValue) range.minValue else range.maxValue, { _, value ->
                    log(CONTROL, "시트 열선", "set 응답 $value")
                    later { seat.getHvacSeatTemperature(area, ok(CONTROL, "시트 열선 3초 후"), fail(CONTROL, "시트 열선 3초 후")) }
                }, fail(CONTROL, "시트 열선 set"))
            }, fail(CONTROL, "시트 열선 get"))
        }, fail(CONTROL, "시트 열선 지원 영역"))
    }

    private fun testHvacTemperature() {
        val hvac = vehicle.getHVAC()
        hvac.checkHvacTemperatureCapabilities({ bounds: Map<SeatArea, AreaBounds<Float>> ->
            log(CONTROL, "온도 지원 영역", bounds.entries.joinToString { "${it.key.values.contentToString()}=${it.value}" })
            val area = bounds.keys.firstOrNull() ?: return@checkHvacTemperatureCapabilities
            hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { before ->
                log(CONTROL, "설정 온도", "변경 전 $before")
                hvac.setHvacTemperature(area, if (before == 21f) 23f else 21f, TemperatureUnit.CELSIUS, { _, value ->
                    log(CONTROL, "설정 온도", "set 응답 $value")
                    later { hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok(CONTROL, "설정 온도 3초 후"), fail(CONTROL, "설정 온도 3초 후")) }
                }, fail(CONTROL, "설정 온도 set"))
            }, fail(CONTROL, "설정 온도 get"))
        }, fail(CONTROL, "온도 지원 영역"))
    }

    private fun later(block: () -> Unit) = handler.postDelayed(block, 3_000)

    // ③ AAOS Car API: Pleos 문서상 속도·기어·외기온도는 여기서 읽고, ADAS 상태 속성이 있는지도 확인
    private fun readCarProperties() {
        try {
            val connected = Car.createCar(this).also { car = it }
            val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val configs = manager.propertyList
            log(AAOS, "읽을 수 있는 속성 수", "${configs.size}")
            log(AAOS, "속성 목록", configs.joinToString { VehiclePropertyIds.toString(it.propertyId) })
            configs.filter { INTERESTING.containsMatchIn(VehiclePropertyIds.toString(it.propertyId)) }.forEach { config ->
                val name = VehiclePropertyIds.toString(config.propertyId)
                config.areaIds.forEach { areaId ->
                    val value = runCatching { manager.getProperty<Any>(config.propertyId, areaId).value.describe() }
                        .getOrElse { "FAIL ${it::class.simpleName}: ${it.message}" }
                    log(AAOS, "$name (area $areaId)", value)
                }
            }
        } catch (t: Throwable) {
            log(AAOS, "Car 연결", "FAIL ${t::class.simpleName}: ${t.message}")
        }
    }

    // ④ ADAS 서비스: 공개 클라이언트 SDK가 없어 바인딩 가능 여부와 인터페이스 이름까지만 확인
    private fun bindAdas() {
        val intent = Intent(ADAS_ACTION).setPackage(ADAS_PACKAGE)
        adasBound = try {
            bindService(intent, adasConnection, BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            log(ADAS, "bindService", "FAIL SecurityException: ${e.message}")
            false
        }
        log(ADAS, "bindService 호출", "$adasBound")
    }

    private fun <T> ok(section: String, name: String): (T) -> Unit = { log(section, name, "OK ${it.describe()}") }

    private fun fail(section: String, name: String): (Exception) -> Unit =
        { log(section, name, "FAIL ${it::class.simpleName}: ${it.message}") }

    private fun log(section: String, name: String, result: String) {
        val line = "[$section] $name: $result"
        Log.i(TAG, line)
        runOnUiThread { lines += line }
    }

    private fun Any?.describe(): String = when (this) {
        is IntArray -> contentToString()
        is FloatArray -> contentToString()
        is LongArray -> contentToString()
        is Array<*> -> contentToString()
        else -> toString()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (adasBound) unbindService(adasConnection)
        car?.disconnect()
        runCatching { vehicle.release() }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PleosDiag"
        const val PERM = "권한"
        const val VEHICLE = "Vehicle 조회"
        const val CONTROL = "Vehicle 제어"
        const val AAOS = "AAOS"
        const val ADAS = "ADAS"
        const val ADAS_ACTION = "ai.pleos.playground.intent.action.ADAS_START"
        const val ADAS_PACKAGE = "ai.pleos.playground.service.adas"
        val INTERESTING = Regex("SPEED|GEAR|TEMPERATURE|WARNING|ASSIST|CRUISE|BRAKING|DISTANCE|BLIND|LANE|COLLISION|DROWSINESS|DISTRACTION|HANDS|PARKING")
    }
}
