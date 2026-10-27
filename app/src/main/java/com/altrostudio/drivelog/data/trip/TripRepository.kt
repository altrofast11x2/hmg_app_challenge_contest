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

    /** 아직 마치지 않은 운행을 지운다 (기어만 잠깐 바꾼 0 km 운행). 마친 운행은 해시 체인에 있어 지우지 않는다 */
    suspend fun discardTrip(tripId: String)

    /** 완료된 운행을 분류한다. 분류 이력은 해시 체인에 남고, 비업무면 주소·위치를 지운다 */
    suspend fun classify(tripId: String, purpose: TripPurpose, actor: String, at: Long)

    fun observeTrips(): Flow<List<TripEntity>>

    /** [from] 이상 [to] 미만에 시작한 완료 운행 (월별 운행기록부) */
    fun observeCompletedTrips(from: Long, to: Long): Flow<List<TripEntity>>

    /** 진행 중(주행 중 또는 정차)인 운행 */
    fun observeActiveTrip(): Flow<TripEntity?>

    /** 같은 목적지에서 지난번에 고른 업무 분류. 비업무는 주소를 남기지 않으므로 제안할 수 없다 */
    suspend fun suggestPurpose(address: String): TripPurpose?

    suspend fun verifyChain(vehicleId: String): ChainVerification

    /** 동의를 철회하면 모든 운행 기록을 지운다 */
    suspend fun deleteAll()

    /** 서버에 아직 올리지 않은 완료 운행과 분류·수정 이력 (체인 순서) */
    suspend fun pendingUploads(limit: Int): PendingUploads

    /** 올린 기록을 표시한다. 올리는 도중 바뀐 운행은 다음에 다시 올라가도록 그대로 둔다 */
    suspend fun markUploaded(trips: List<TripEntity>, changes: List<TripChangeEntity>)

    /** 올릴 기록 수 */
    fun observePendingCount(): Flow<Int>

    /** 조직에 연결되기 전에 쌓인 운행에 조직과 기본 운전자를 채운다 (해시 대상이 아니라 체인은 그대로다) */
    suspend fun assignOrg(orgId: String, driverId: String?)

    suspend fun chainHeads(): List<ChainHeadEntity>

    /**
     * 그 차량 해시 체인의 ID = 첫 기록(seq 1)의 해시. 아직 기록이 없으면 null.
     * 동의를 철회하거나 앱을 다시 설치하면 같은 차(VIN)에서 체인이 seq 1부터 새로 시작하므로, 서버는 이 ID로 체인을 구분한다
     */
    suspend fun chainId(vehicleId: String): String?
}

data class PendingUploads(val trips: List<TripEntity>, val changes: List<TripChangeEntity>) {
    val isEmpty: Boolean get() = trips.isEmpty() && changes.isEmpty()
}
