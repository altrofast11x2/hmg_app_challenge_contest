package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendApi
import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.PairRequest
import com.altrostudio.drivelog.data.trip.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** 이 차량이 연결된 조직과 기본 운전자 */
@Serializable
data class Pairing(
    val orgId: String,
    val orgName: String,
    val driverId: String?,
    val driverName: String?
)

sealed interface PairingResult {
    data class Connected(val pairing: Pairing) : PairingResult
    data object InvalidCode : PairingResult
    data object ExpiredCode : PairingResult
    data object TooManyAttempts : PairingResult
    data object VehicleInOtherOrg : PairingResult
    data object Offline : PairingResult
    data object NotConfigured : PairingResult
    data class Failed(val message: String) : PairingResult
}

/**
 * 관리자 웹에서 발급한 6자리 연결 코드로 이 차량을 조직에 등록한다 (정차 중에만).
 * 연결 전에 쌓인 운행도 연결한 조직으로 올라간다.
 */
class PairingRepository(
    private val api: BackendApi,
    private val auth: DeviceAuth,
    private val preferences: SyncSettings,
    private val vehicleId: suspend () -> String,
    private val trips: TripRepository,
    val isConfigured: Boolean
) {
    private val _pairing = MutableStateFlow(preferences.pairing)
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()

    private val _skipped = MutableStateFlow(preferences.pairingSkipped)
    val skipped: StateFlow<Boolean> = _skipped.asStateFlow()

    suspend fun pair(code: String): PairingResult = try {
        val request = PairRequest(code = code, vehicleId = vehicleId(), vin = preferences.vin)
        val response = auth.withToken { api.pairVehicle(it, request) }
        when (response.status) {
            "OK" -> {
                val connected = Pairing(
                    orgId = checkNotNull(response.orgId),
                    orgName = response.orgName.orEmpty(),
                    driverId = response.driverId,
                    driverName = response.driverName
                )
                preferences.pairing = connected
                setSkipped(false)
                _pairing.value = connected
                trips.assignOrg(connected.orgId, connected.driverId)
                PairingResult.Connected(connected)
            }
            "INVALID_CODE" -> PairingResult.InvalidCode
            "EXPIRED_CODE" -> PairingResult.ExpiredCode
            "TOO_MANY_ATTEMPTS" -> PairingResult.TooManyAttempts
            "VEHICLE_IN_OTHER_ORG" -> PairingResult.VehicleInOtherOrg
            else -> PairingResult.Failed(response.status)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackendException) {
        when (e.kind) {
            BackendException.Kind.NETWORK -> PairingResult.Offline
            BackendException.Kind.NOT_CONFIGURED -> PairingResult.NotConfigured
            else -> PairingResult.Failed(e.message.orEmpty())
        }
    } catch (e: Exception) {
        // 권한 오류, 예상 밖의 응답 형식 등 어떤 이유로 실패해도 앱은 멈추지 않고 실패를 알린다
        PairingResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    /**
     * 동의 철회: 서버의 차량 연결도 끊는다 (전에는 차 안 기록만 지워지고 관리자 웹에는 계속 "연결됨"이었다).
     * 인터넷이 없거나 실패해도 철회는 막지 않는다 (관리자가 웹에서 [연결 해제]할 수 있다). 끊었으면 true
     */
    suspend fun disconnectFromServer(): Boolean {
        if (preferences.pairing == null) return false
        return try {
            withTimeout(DISCONNECT_TIMEOUT_MS) { auth.withToken { api.unpairOwnVehicle(it) } }
            true
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) false else throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 서버에서 연결이 끊겼다 (관리자가 웹에서 연결 해제, 기업 삭제). 기록은 그대로 두고 연결 화면에서 다시 연결하게 한다.
     * 기기 계정도 새로 만든다 (기업 삭제로 서버에서 지워졌을 수 있다)
     */
    fun forget() {
        preferences.pairing = null
        preferences.deviceSession = null
        _pairing.value = null
        setSkipped(false)
    }

    /** [나중에 연결]: 연결하지 않고 이 차량 안에서만 기록한다. 설정에서 언제든 연결할 수 있다 */
    fun skip() = setSkipped(true)

    /** 동의 철회로 설정이 지워졌을 때 화면 상태도 처음으로 돌린다 */
    fun reset() {
        _pairing.value = null
        _skipped.value = false
    }

    private fun setSkipped(value: Boolean) {
        preferences.pairingSkipped = value
        _skipped.value = value
    }

    private companion object {
        const val DISCONNECT_TIMEOUT_MS = 5_000L
    }
}
