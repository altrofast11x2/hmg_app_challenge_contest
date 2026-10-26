package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendApi
import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceSession
import com.altrostudio.drivelog.data.remote.PairRequest
import com.altrostudio.drivelog.data.remote.PairResponse
import com.altrostudio.drivelog.data.remote.RemoteChainHead
import com.altrostudio.drivelog.data.remote.RemoteTrip
import com.altrostudio.drivelog.data.remote.RemoteTripChange

/** 서버를 흉내 낸다. 올라온 기록은 id로 덮어써서 실제 서버(upsert)처럼 중복이 생기지 않는다 */
class FakeBackendApi : BackendApi {

    val trips = mutableMapOf<String, RemoteTrip>()
    val changes = mutableMapOf<String, RemoteTripChange>()
    val chainHeads = mutableMapOf<String, RemoteChainHead>()
    val usedTokens = mutableListOf<String>()
    var lastPairRequest: PairRequest? = null
    var pairResponse = PairResponse(
        status = "OK", orgId = "org-1", orgName = "데모 조직", vehicleId = "VIN-TEST", driverId = "driver-1", driverName = "기본 운전자"
    )

    /** 다음 호출부터 이 오류를 던진다 */
    var failure: BackendException? = null

    /** 다음 한 번은 토큰 만료(401)로 거부한다 */
    var rejectTokenOnce = false

    private var sessionCount = 0

    override suspend fun signInAnonymously() = newSession("refresh-1")

    override suspend fun refresh(refreshToken: String) = newSession(refreshToken)

    override suspend fun pairVehicle(accessToken: String, request: PairRequest): PairResponse {
        accept(accessToken)
        lastPairRequest = request
        return pairResponse
    }

    override suspend fun upsertTrips(accessToken: String, trips: List<RemoteTrip>) {
        accept(accessToken)
        trips.forEach { this.trips[it.id] = it }
    }

    override suspend fun upsertChanges(accessToken: String, changes: List<RemoteTripChange>) {
        accept(accessToken)
        changes.forEach { this.changes[it.id] = it }
    }

    override suspend fun upsertChainHead(accessToken: String, head: RemoteChainHead) {
        accept(accessToken)
        chainHeads[head.vehicleId] = head
    }

    private fun newSession(refreshToken: String) =
        DeviceSession("access-${++sessionCount}", refreshToken, expiresAtMs = Long.MAX_VALUE, userId = "device-user")

    private fun accept(token: String) {
        failure?.let { throw it }
        if (rejectTokenOnce) {
            rejectTokenOnce = false
            throw BackendException(BackendException.Kind.UNAUTHORIZED, "JWT expired")
        }
        usedTokens += token
    }
}

class MemorySyncSettings(override var vin: String? = null) : SyncSettings {
    override var deviceSession: DeviceSession? = null
    override var pairing: Pairing? = null
    override var pairingSkipped: Boolean = false
}
