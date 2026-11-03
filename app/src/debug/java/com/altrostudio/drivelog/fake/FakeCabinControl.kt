package com.altrostudio.drivelog.fake

import com.altrostudio.drivelog.data.vehicle.AmbientLight
import com.altrostudio.drivelog.data.vehicle.CabinControl
import com.altrostudio.drivelog.data.vehicle.CabinState
import com.altrostudio.drivelog.data.vehicle.CabinWindow
import com.altrostudio.drivelog.data.vehicle.Comfort

/**
 * debug 빌드·단위 테스트 전용 차량 제어. 실제 차처럼 값이 바뀌고, [accepts]가 false면 차가 요청을 받아들이지 않는 것처럼 동작한다.
 * 무드등 색 변화는 [ambientHistory]에 남는다 (신호 순서 확인용)
 */
class FakeCabinControl(
    var tempC: Float? = 22f,
    var seatHeat: Int? = 0,
    var ambient: AmbientLight = AmbientLight(color = 0xFFFFFF, brightness = 40, on = false),
    var openWindows: MutableSet<CabinWindow> = mutableSetOf(),
    var doorsLocked: Boolean? = false,
    var cabinLightOn: Boolean? = false
) : CabinControl {

    var accepts = true
    val ambientHistory = mutableListOf<Int?>()

    override suspend fun readComfort() = Comfort(tempC, seatHeat, ambient.color)

    override suspend fun applyComfort(target: Comfort): Comfort {
        if (!accepts) return Comfort()
        target.tempC?.let { tempC = it.coerceIn(15f, 30f) }
        target.seatHeat?.let { seatHeat = it.coerceIn(-3, 3) }
        target.ambientColor?.let { setColor(it) }
        return Comfort(target.tempC?.let { tempC }, target.seatHeat?.let { seatHeat }, target.ambientColor)
    }

    override suspend fun readAmbient(): AmbientLight = ambient

    override suspend fun showAmbient(color: Int, minBrightness: Int): Boolean {
        if (!accepts) return false
        ambient = ambient.copy(on = true, brightness = maxOf(ambient.brightness ?: 0, minBrightness))
        setColor(color)
        return true
    }

    override suspend fun restoreAmbient(saved: AmbientLight): Boolean {
        ambient = saved
        ambientHistory += saved.color
        return true
    }

    override suspend fun readCabin() = CabinState(openWindows.toSet(), doorsLocked, cabinLightOn)

    override suspend fun closeWindows(): Boolean {
        if (!accepts) return false
        openWindows.clear()
        return true
    }

    override suspend fun turnOffCabinLight(): Boolean {
        if (!accepts) return false
        cabinLightOn = false
        return true
    }

    override suspend fun lockDoors(): Boolean {
        if (!accepts) return false
        doorsLocked = true
        return true
    }

    private fun setColor(color: Int) {
        ambient = ambient.copy(color = color and 0xFFFFFF)
        ambientHistory += ambient.color
    }
}
