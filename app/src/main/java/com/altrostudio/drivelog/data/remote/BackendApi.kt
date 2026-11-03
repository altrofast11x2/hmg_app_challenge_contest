package com.altrostudio.drivelog.data.remote

/** 서버(Supabase) 호출. 실제 구현은 [OkHttpBackendApi], 테스트는 Fake 구현을 쓴다 */
interface BackendApi {

    /** 비밀번호 없이 차량용 기기 계정을 만든다 */
    suspend fun signInAnonymously(): DeviceSession

    suspend fun refresh(refreshToken: String): DeviceSession

    suspend fun pairVehicle(accessToken: String, request: PairRequest): PairResponse

    /** 같은 id는 덮어쓴다 (여러 번 올려도 한 줄만 남는다) */
    suspend fun upsertTrips(accessToken: String, trips: List<RemoteTrip>)

    suspend fun upsertChanges(accessToken: String, changes: List<RemoteTripChange>)

    suspend fun upsertChainHead(accessToken: String, head: RemoteChainHead)

    /** 이 차량의 조직 연결을 끊는다 (동의 철회). 운행 기록은 서버에 남는다 */
    suspend fun unpairOwnVehicle(accessToken: String)

    /** 이 기기 계정이 지금도 그 차량의 기기인지. 관리자가 웹에서 연결을 해제했거나 기업이 삭제되면 false */
    suspend fun isVehicleDevice(accessToken: String, vehicleId: String): Boolean

    /** 휴대폰으로 받기: 이 차량의 기간 운행기록부를 10분 동안 여는 링크 토큰 */
    suspend fun createShareLink(accessToken: String, request: ShareLinkRequest): ShareLinkResponse

    /** 이 차에서 고를 수 있는 운전자 명단 (승인된 직원은 서버가 운전자로 만든다) */
    suspend fun driverRoster(accessToken: String): List<RemoteDriver>

    /** 운전자의 맞춤 설정을 저장한다. 그 운전자가 이 조직에 없으면 false */
    suspend fun saveComfort(accessToken: String, request: SaveComfortRequest): Boolean

    /** 운행을 마쳤을 때의 차 상태를 보고한다 (늦게 보고된 것만 남는다) */
    suspend fun reportStatus(accessToken: String, request: ReportStatusRequest)
}

/** [httpCode]: 서버가 답한 HTTP 상태 코드 (응답을 받지 못했으면 null) */
class BackendException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
    val httpCode: Int? = null
) : Exception(message, cause) {
    enum class Kind {
        /** 인터넷 연결 없음·시간 초과 */
        NETWORK,

        /** 토큰 만료·거부 (새 토큰으로 다시 시도) */
        UNAUTHORIZED,
        SERVER,

        /** 서버 주소·키 없이 빌드됨 */
        NOT_CONFIGURED
    }
}
