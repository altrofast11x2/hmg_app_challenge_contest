package com.altrostudio.drivelog.data.trip

import com.altrostudio.drivelog.data.navi.NaviArrival
import com.altrostudio.drivelog.data.vehicle.EnergyEfficiency
import kotlinx.coroutines.flow.Flow

/** 운행 기록 저장소. 실제 구현은 [RoomTripRepository], 테스트용은 Fake 구현을 쓴다 */
interface TripRepository {

    /** 진행 중(주행 중 또는 정차)인 운행 */
    suspend fun activeTrip(): TripEntity?

    suspend fun startTrip(tripId: String, vehicleId: String, at: Long, odometerKm: Double?)

    /** 시작할 때 늦게 도착하는 값(배터리, 주소)을 채운다 */
    suspend fun updateStart(tripId: String, socPercent: Int?, address: String?)

    /** 주행 중임을 확인한 시각을 남긴다 */
    suspend fun touch(tripId: String, at: Long)

    suspend fun markParked(tripId: String, at: Long, odometerKm: Double?)

    /** 정차할 때 늦게 도착하는 값(배터리, 주소, 연비·전비)을 채운다 */
    suspend fun updateEnd(tripId: String, socPercent: Int?, address: String?, efficiency: EnergyEfficiency?)

    /** 정차했다가 다시 출발하면 같은 운행을 이어 간다 */
    suspend fun resumeDriving(tripId: String, at: Long)

    suspend fun addStop(tripId: String, arrival: NaviArrival, at: Long)

    /** 운행을 마치고 해시 체인에 붙인다 */
    suspend fun completeTrip(tripId: String, at: Long)

    /** 완료된 운행을 분류한다. 분류 이력은 해시 체인에 남고, 비업무면 주소·위치를 지운다 */
    suspend fun classify(tripId: String, purpose: TripPurpose, actor: String, at: Long)

    fun observeTrips(): Flow<List<TripEntity>>

    suspend fun verifyChain(vehicleId: String): ChainVerification
}
