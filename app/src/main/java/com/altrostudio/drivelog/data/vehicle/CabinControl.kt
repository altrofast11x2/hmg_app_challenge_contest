package com.altrostudio.drivelog.data.vehicle

/**
 * 운전자 맞춤 설정: 운전석 공조 온도(℃), 운전석 시트 열선(1~3, 음수는 통풍, 0은 끔), 무드등 색(0xRRGGBB).
 * 값이 null이면 그 항목은 건드리지 않는다
 */
data class Comfort(val tempC: Float? = null, val seatHeat: Int? = null, val ambientColor: Int? = null) {
    val isEmpty: Boolean get() = tempC == null && seatHeat == null && ambientColor == null
}

/** 무드등 상태. 잠깐 신호로 바꾼 뒤 그대로 되돌리려고 저장한다 */
data class AmbientLight(val color: Int?, val brightness: Int?, val on: Boolean?)

enum class CabinWindow { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT }

/** 하차 점검에 쓰는 차 안 상태. 읽지 못한(지원하지 않는) 값은 null */
data class CabinState(
    val openWindows: Set<CabinWindow>?,
    val doorsLocked: Boolean?,
    val cabinLightOn: Boolean?
)

/**
 * 차량 제어 (Pleos Vehicle SDK의 공조·시트·조명·창문·문 API). 실제 구현은 [PleosCabinControl], 시험용은 Fake 구현.
 * - 모든 제어는 지원 범위(check*Capabilities)를 먼저 확인하고, 바꾼 뒤 다시 읽어 실제로 바뀐 것만 성공으로 본다
 *   (Vehicle SDK 샘플 앱 가이드: 변경 요청 완료만으로 값이 바뀌었다고 판단하지 않는다)
 * - 언제 제어해도 되는지(정차 중, 운전자가 직접 누름)는 이 인터페이스를 쓰는 쪽이 정한다
 */
interface CabinControl {

    /** 지금 차의 맞춤 설정 값 */
    suspend fun readComfort(): Comfort

    /** [target]의 값으로 바꾸고, 실제로 바뀐 항목만 돌려준다 */
    suspend fun applyComfort(target: Comfort): Comfort

    suspend fun readAmbient(): AmbientLight?

    /** 무드등을 [color]로 켠다. 꺼져 있거나 [minBrightness]보다 어두우면 켜고 밝힌다 */
    suspend fun showAmbient(color: Int, minBrightness: Int): Boolean

    /** [showAmbient] 전에 읽어 둔 상태로 되돌린다 */
    suspend fun restoreAmbient(saved: AmbientLight): Boolean

    suspend fun readCabin(): CabinState

    /** 열린 창문을 모두 닫는다. 닫힌 것을 확인하면 true */
    suspend fun closeWindows(): Boolean

    suspend fun turnOffCabinLight(): Boolean

    suspend fun lockDoors(): Boolean
}

/** 차량 OS가 아니거나 제어를 쓰지 않는 빌드: 아무것도 하지 않는다 */
object NoCabinControl : CabinControl {
    override suspend fun readComfort() = Comfort()
    override suspend fun applyComfort(target: Comfort) = Comfort()
    override suspend fun readAmbient(): AmbientLight? = null
    override suspend fun showAmbient(color: Int, minBrightness: Int) = false
    override suspend fun restoreAmbient(saved: AmbientLight) = false
    override suspend fun readCabin() = CabinState(null, null, null)
    override suspend fun closeWindows() = false
    override suspend fun turnOffCabinLight() = false
    override suspend fun lockDoors() = false
}
