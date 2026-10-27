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

    /** 휴대폰으로 받기: 이 차량의 기간 운행기록부를 10분 동안 여는 링크 토큰 */
    suspend fun createShareLink(accessToken: String, request: ShareLinkRequest): ShareLinkResponse
}

class BackendException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
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
