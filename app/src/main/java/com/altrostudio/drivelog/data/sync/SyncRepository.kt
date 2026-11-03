package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendApi
import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.RemoteChainHead
import com.altrostudio.drivelog.data.remote.RemoteTrip
import com.altrostudio.drivelog.data.remote.RemoteTripChange
import com.altrostudio.drivelog.data.trip.TripChangeEntity
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncOutcome {
    data object NotPaired : SyncOutcome
    data object UpToDate : SyncOutcome
    data class Uploaded(val trips: Int, val changes: Int) : SyncOutcome
    data object Offline : SyncOutcome

    /** 서버에서 이 차량의 연결이 끊겼다 (관리자가 웹에서 연결 해제, 기업 삭제). 기록은 남고 다시 연결하면 올라간다 */
    data object Unpaired : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * 오프라인 우선 동기화. 운행은 먼저 이 차량(Room)에 저장되고, 조직에 연결된 뒤 인터넷이 되면 올라간다.
 * 서버는 앱이 만든 id로 덮어쓰므로 같은 기록을 여러 번 올려도 중복되지 않는다. 실패하면 다음에 다시 시도한다.
 * 백엔드를 바꿔도 앱 로직(기록·분류)은 이 클래스만 바꾸면 된다.
 */
class SyncRepository(
    private val api: BackendApi,
    private val auth: DeviceAuth,
    private val pairing: StateFlow<Pairing?>,
    private val trips: TripRepository,
    private val vehicleId: suspend () -> String,
    private val onUnpaired: () -> Unit,
    /** 운전자 명단·맞춤 설정·차량 상태 (공용차 연동). 없으면 운행 기록만 올린다 */
    private val cabinSync: CabinSync? = null
) {
    private val mutex = Mutex()
    private val _lastOutcome = MutableStateFlow<SyncOutcome?>(null)
    val lastOutcome: StateFlow<SyncOutcome?> = _lastOutcome.asStateFlow()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        val outcome = try {
            uploadPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendException) {
            when {
                e.kind == BackendException.Kind.NETWORK -> SyncOutcome.Offline
                // 권한 거부(403)는 서버에서 연결이 끊겼을 때 생긴다. 확인되면 연결을 잊어 운전자가 다시 연결하게 한다
                // (전에는 "연결됨"으로 보이면서 계속 실패했다, 2026-10-27 점검)
                e.httpCode == HTTP_FORBIDDEN && !stillPaired() -> {
                    onUnpaired()
                    SyncOutcome.Unpaired
                }
                else -> SyncOutcome.Failed(e.message.orEmpty())
            }
        } catch (e: Exception) {
            // 어떤 이유로 실패해도 기록은 이 차량에 그대로 남고 다음에 다시 올린다 (앱은 멈추지 않는다)
            SyncOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
        // 운행을 올릴 수 있는 상태였을 때만 차 안 연동 값도 주고받는다 (실패해도 운행 동기화 결과는 그대로)
        if (outcome is SyncOutcome.Uploaded || outcome == SyncOutcome.UpToDate) {
            try {
                cabinSync?.sync(auth)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Unit
            }
        }
        _lastOutcome.value = outcome
        outcome
    }

    private suspend fun uploadPending(): SyncOutcome {
        val current = pairing.value ?: return SyncOutcome.NotPaired
        trips.assignOrg(current.orgId, current.driverId)
        var tripCount = 0
        var changeCount = 0
        // 서버는 (차량, 체인 ID, seq)로 기록을 구분한다. 동의 철회 뒤 같은 차의 새 체인이 옛 체인과 부딪히지 않게 한다
        val chainIds = mutableMapOf<String, String>()
        suspend fun chainId(vehicleId: String): String = chainIds.getOrPut(vehicleId) {
            checkNotNull(trips.chainId(vehicleId)) { "체인의 첫 기록이 없습니다" }
        }
        // 업로드 도중 기록이 또 바뀌면 같은 묶음이 다시 나올 수 있어 횟수를 제한한다
        for (round in 1..MAX_BATCHES) {
            val batch = trips.pendingUploads(BATCH_SIZE)
            if (batch.isEmpty) break
            // 서버의 분류 이력은 운행을 참조한다. 올릴 운행이 한 묶음보다 많이 남았으면 이 묶음에 없는 운행의 이력이 섞여
            // 서버가 거부하고 같은 묶음에서 계속 멈출 수 있어, 운행을 다 올린 뒤에 이력을 올린다 (2026-10-27 점검)
            val changes = if (batch.trips.size < BATCH_SIZE) batch.changes else emptyList()
            val remoteTrips = batch.trips.map { it.toRemote(current, chainId(it.vehicleId)) }
            val remoteChanges = changes.map { it.toRemote(current.orgId, chainId(it.vehicleId)) }
            auth.withToken { token ->
                // 분류 이력이 운행을 참조하므로 운행을 먼저 올린다
                if (remoteTrips.isNotEmpty()) api.upsertTrips(token, remoteTrips)
                if (remoteChanges.isNotEmpty()) api.upsertChanges(token, remoteChanges)
            }
            trips.markUploaded(batch.trips, changes)
            tripCount += batch.trips.size
            changeCount += changes.size
        }
        // 기록 업로드 후 머리 업로드만 실패해도 다음 동기화에서 다시 시도한다.
        // 아직 미전송 기록이 남았다면 서버에 없는 미래의 머리를 올리지 않는다.
        if (!trips.pendingUploads(1).isEmpty) return SyncOutcome.Uploaded(tripCount, changeCount)
        // 체인 머리를 마지막에 올려 서버에서 끝부분 누락을 확인할 수 있게 한다
        trips.chainHeads().forEach { head ->
            val remoteHead = RemoteChainHead(head.vehicleId, current.orgId, chainId(head.vehicleId), head.seq, head.hash)
            auth.withToken { api.upsertChainHead(it, remoteHead) }
        }
        return if (tripCount + changeCount == 0) SyncOutcome.UpToDate else SyncOutcome.Uploaded(tripCount, changeCount)
    }

    /** 확인하지 못하면(인터넷 끊김 등) 연결된 것으로 본다 */
    private suspend fun stillPaired(): Boolean = try {
        auth.withToken { api.isVehicleDevice(it, vehicleId()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true
    }

    private fun TripEntity.toRemote(pairing: Pairing, chainId: String) = RemoteTrip(
        id = id,
        orgId = orgId ?: pairing.orgId,
        vehicleId = vehicleId,
        driverId = driverId ?: pairing.driverId,
        startedAtMs = startedAt,
        endedAtMs = endedAt,
        startOdometerKm = startOdometerKm,
        endOdometerKm = endOdometerKm,
        odometerSource = odometerSource.name,
        startSocPercent = startSocPercent,
        endSocPercent = endSocPercent,
        efficiency = efficiency,
        efficiencyType = efficiencyType?.name,
        startAddress = startAddress,
        endAddress = endAddress,
        visitNote = visitNote,
        purpose = purpose.name,
        chainId = chainId,
        chainSeq = checkNotNull(chainSeq),
        prevHash = checkNotNull(prevHash),
        hash = checkNotNull(hash),
        driverName = driverName,
        driverDepartment = driverDepartment,
        workNote = workNote
    )

    private fun TripChangeEntity.toRemote(orgId: String, chainId: String) = RemoteTripChange(
        id = id,
        orgId = orgId,
        tripId = tripId,
        vehicleId = vehicleId,
        field = field.name,
        oldValue = oldValue,
        newValue = newValue,
        actor = actor,
        reason = reason,
        createdAtMs = createdAt,
        chainId = chainId,
        chainSeq = chainSeq,
        prevHash = prevHash,
        hash = hash
    )

    private companion object {
        const val BATCH_SIZE = 100
        const val MAX_BATCHES = 20
        const val HTTP_FORBIDDEN = 403
    }
}
