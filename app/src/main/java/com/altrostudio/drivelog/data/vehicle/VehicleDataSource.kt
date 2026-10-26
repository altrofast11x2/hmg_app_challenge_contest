package com.altrostudio.drivelog.data.vehicle

import kotlinx.coroutines.flow.Flow

/**
 * 차량 상태 조회. 실제 구현은 [PleosVehicleDataSource], 테스트용은 Fake 구현을 쓴다.
 *
 * suspend 함수는 SDK가 실패를 알리면 예외를 던지고, 지원하지 않거나 응답이 없으면 null을 돌려준다.
 */
interface VehicleDataSource {

    /** 계기판 누적 주행거리(km) */
    suspend fun readOdometerKm(): Float?

    /** 차량 식별번호(VIN) */
    suspend fun readVin(): String?

    /** 지금까지의 평균 연비 또는 전비 (차량이 지원할 때만) */
    suspend fun readEfficiency(): EnergyEfficiency?

    /** 전기차 배터리 잔량(%). 운행 전후로 기록해 전비 대신 배터리 사용량을 남긴다. 전기차가 아니면 null */
    suspend fun readBatterySocPercent(): Int?

    /** 계기판 누적 주행거리 변화 (km) */
    val odometerKm: Flow<Float?>

    /** 기어 위치 변화 */
    val gear: Flow<GearPosition>

    /** 주차 브레이크 상태 변화. 지원하지 않으면 null */
    val parkingBrake: Flow<Boolean?>

    /** 플랫폼이 판단한 주행 제한 여부. true면 주행 중이라 화면 조작을 막아야 한다 */
    val drivingRestricted: Flow<Boolean>
}

enum class GearPosition { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class EnergyType { FUEL, ELECTRIC }

data class EnergyEfficiency(val value: Float, val type: EnergyType)
