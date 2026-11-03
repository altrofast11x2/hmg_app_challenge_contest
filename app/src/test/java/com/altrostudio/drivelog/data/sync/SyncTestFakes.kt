package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendApi
import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceSession
import com.altrostudio.drivelog.data.remote.PairRequest
import com.altrostudio.drivelog.data.remote.PairResponse
import com.altrostudio.drivelog.data.remote.RemoteChainHead
import com.altrostudio.drivelog.data.remote.RemoteComfort
import com.altrostudio.drivelog.data.remote.RemoteDriver
import com.altrostudio.drivelog.data.remote.RemoteTrip
import com.altrostudio.drivelog.data.remote.RemoteTripChange
import com.altrostudio.drivelog.data.remote.RemoteVehicleStatus
import com.altrostudio.drivelog.data.remote.ReportStatusRequest
import com.altrostudio.drivelog.data.remote.SaveComfortRequest
import com.altrostudio.drivelog.data.remote.ShareLinkRequest
import com.altrostudio.drivelog.data.remote.ShareLinkResponse

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
    var failure: Exception? = null

    /** 다음 한 번은 토큰 만료(401)로 거부한다 */
    var rejectTokenOnce = false

    /** 이 기기 계정이 그 차량의 기기인지 (관리자가 웹에서 연결을 해제하면 false) */
    var isDevice = true

    /** 올리기를 권한 거부(403)로 막는다 (서버에서 연결이 끊긴 뒤의 서버처럼) */
    var forbidUploads = false
    var failChainHeadOnce = false

    /** 다음 토큰 갱신을 이 오류로 거부한다 (기기 계정이 서버에서 지워짐) */
    var refreshFailure: Exception? = null

    var signIns = 0
        private set

    private var sessionCount = 0

    override suspend fun signInAnonymously(): DeviceSession {
        signIns++
        return newSession("refresh-1")
    }

    override suspend fun refresh(refreshToken: String): DeviceSession {
        refreshFailure?.let {
            refreshFailure = null
            throw it
        }
        return newSession(refreshToken)
    }

    override suspend fun isVehicleDevice(accessToken: String, vehicleId: String): Boolean {
        accept(accessToken)
        return isDevice
    }

    override suspend fun pairVehicle(accessToken: String, request: PairRequest): PairResponse {
        accept(accessToken)
        lastPairRequest = request
        return pairResponse
    }

    override suspend fun upsertTrips(accessToken: String, trips: List<RemoteTrip>) {
        accept(accessToken)
        forbidIfUnpaired()
        trips.forEach { this.trips[it.id] = it }
    }

    override suspend fun upsertChanges(accessToken: String, changes: List<RemoteTripChange>) {
        accept(accessToken)
        forbidIfUnpaired()
        // 서버처럼 아직 없는 운행을 가리키는 이력은 거부한다 (trip_changes.trip_id 외래 키)
        if (changes.any { it.tripId !in trips }) throw BackendException(BackendException.Kind.SERVER, "foreign key violation")
        changes.forEach { this.changes[it.id] = it }
    }

    override suspend fun upsertChainHead(accessToken: String, head: RemoteChainHead) {
        accept(accessToken)
        forbidIfUnpaired()
        if (failChainHeadOnce) {
            failChainHeadOnce = false
            throw BackendException(BackendException.Kind.NETWORK, "offline")
        }
        chainHeads[head.vehicleId] = head
    }

    var unpairCalls = 0

    override suspend fun unpairOwnVehicle(accessToken: String) {
        accept(accessToken)
        unpairCalls++
    }

    var lastShareRequest: ShareLinkRequest? = null
    var shareResponse = ShareLinkResponse(status = "OK", token = "token-1", expiresAt = "2026-10-27T05:34:12.123456+00:00")

    override suspend fun createShareLink(accessToken: String, request: ShareLinkRequest): ShareLinkResponse {
        accept(accessToken)
        lastShareRequest = request
        return shareResponse
    }

    /** 서버 운전자 명단 (device_driver_roster) */
    var roster = listOf<RemoteDriver>()
    val savedComforts = mutableMapOf<String, RemoteComfort>()
    val statusReports = mutableListOf<RemoteVehicleStatus>()

    /** 0010을 적용하지 않은 서버처럼 차 안 연동 함수를 404로 거부한다 */
    var cabinFunctionsMissing = false

    override suspend fun driverRoster(accessToken: String): List<RemoteDriver> {
        accept(accessToken)
        missingIfOld()
        return roster
    }

    override suspend fun saveComfort(accessToken: String, request: SaveComfortRequest): Boolean {
        accept(accessToken)
        missingIfOld()
        if (roster.none { it.id == request.driverId }) return false
        savedComforts[request.driverId] = request.comfort
        return true
    }

    override suspend fun reportStatus(accessToken: String, request: ReportStatusRequest) {
        accept(accessToken)
        missingIfOld()
        statusReports += request.status
    }

    private fun missingIfOld() {
        if (cabinFunctionsMissing) throw BackendException(BackendException.Kind.SERVER, "HTTP 404: function not found", httpCode = 404)
    }

    private fun newSession(refreshToken: String) =
        DeviceSession("access-${++sessionCount}", refreshToken, expiresAtMs = Long.MAX_VALUE, userId = "device-user")

    private fun forbidIfUnpaired() {
        if (forbidUploads) throw BackendException(BackendException.Kind.SERVER, "HTTP 403: row-level security", httpCode = 403)
    }

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
