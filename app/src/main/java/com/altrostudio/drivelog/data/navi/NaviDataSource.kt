package com.altrostudio.drivelog.data.navi

import kotlinx.coroutines.flow.Flow

/**
 * 내비게이션 정보 조회. 실제 구현은 [PleosNaviDataSource], 테스트용은 Fake 구현을 쓴다.
 * 위치는 운행 시작·종료 시점에만 조회한다 (실시간 위치 추적은 하지 않는다).
 */
interface NaviDataSource {

    /** 현재 위치의 주소. 내비가 응답하지 않으면 null */
    suspend fun currentPlace(): NaviPlace?

    /** 안내 중인 목적지. 경로 안내 중이 아니거나 응답이 없으면 null */
    suspend fun destination(): NaviPlace?

    /** 목적지·경유지 도착 이벤트 */
    val arrivals: Flow<NaviArrival>
}

data class NaviPlace(
    val name: String?,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class NaviArrival(val kind: Kind, val name: String, val address: String) {
    enum class Kind { DESTINATION, WAYPOINT }
}
