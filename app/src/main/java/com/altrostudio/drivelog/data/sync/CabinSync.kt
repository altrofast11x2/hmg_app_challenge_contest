package com.altrostudio.drivelog.data.sync

import android.util.Log
import com.altrostudio.drivelog.data.driver.DriverDao
import com.altrostudio.drivelog.data.driver.DriverEntity
import com.altrostudio.drivelog.data.remote.BackendApi
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.RemoteComfort
import com.altrostudio.drivelog.data.remote.RemoteDriver
import com.altrostudio.drivelog.data.remote.RemoteVehicleStatus
import com.altrostudio.drivelog.data.remote.ReportStatusRequest
import com.altrostudio.drivelog.data.remote.SaveComfortRequest
import kotlinx.coroutines.CancellationException

/** 차 안 연동 동기화에 필요한 저장 값. 실제 구현은 [RoomCabinSyncStore], 단위 테스트는 메모리 구현 */
interface CabinSyncStore {
    suspend fun replaceRoster(roster: List<DriverEntity>)
    suspend fun dirtyComforts(): List<DriverEntity>
    suspend fun markComfortSynced(id: String, updatedAt: Long)

    /** 아직 올리지 못한 마지막 차량 상태 (마지막 것 하나만 둔다) */
    var pendingStatus: RemoteVehicleStatus?

    /** 운전자 명단을 마지막으로 받은 시각 */
    var rosterFetchedAt: Long
}

class RoomCabinSyncStore(private val dao: DriverDao, private val prefs: CabinSyncPrefs) : CabinSyncStore {
    override suspend fun replaceRoster(roster: List<DriverEntity>) = dao.replaceRoster(roster)
    override suspend fun dirtyComforts() = dao.dirtyComforts()
    override suspend fun markComfortSynced(id: String, updatedAt: Long) = dao.markComfortSynced(id, updatedAt)
    override var pendingStatus: RemoteVehicleStatus?
        get() = prefs.pendingStatus
        set(value) {
            prefs.pendingStatus = value
        }
    override var rosterFetchedAt: Long
        get() = prefs.rosterFetchedAt
        set(value) {
            prefs.rosterFetchedAt = value
        }
}

/** AppPreferences에 두는 값 (차량 상태에는 운전자 이름이 있어 암호화해 저장한다) */
interface CabinSyncPrefs {
    var pendingStatus: RemoteVehicleStatus?
    var rosterFetchedAt: Long
}

/** 이번 동기화에서 한 일. 서버에 0010이 아직 없으면 [missingOnServer] */
data class CabinSyncResult(
    val rosterSize: Int? = null,
    val comfortsSaved: Int = 0,
    val statusSent: Boolean = false,
    val missingOnServer: Boolean = false
)

/**
 * 공용차 운전자 명단 받기, 운전자 맞춤 설정 올리기, 운행을 마쳤을 때의 차 상태 보고.
 * 운행 동기화([SyncRepository])가 끝난 뒤 부른다. 하나가 실패해도 나머지와 운행 기록은 그대로 진행한다
 */
class CabinSync(
    private val api: BackendApi,
    private val store: CabinSyncStore,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** 다음 동기화에서 명단을 바로 다시 받는다 (조직에 연결했을 때) */
    fun refreshRosterSoon() {
        store.rosterFetchedAt = 0
    }

    suspend fun sync(auth: DeviceAuth): CabinSyncResult {
        var result = CabinSyncResult()
        if (now() - store.rosterFetchedAt >= ROSTER_TTL_MS) {
            step("운전자 명단") {
                val roster = auth.withToken { api.driverRoster(it) }
                store.replaceRoster(roster.mapIndexed { index, driver -> driver.toEntity(index, now()) })
                store.rosterFetchedAt = now()
                result = result.copy(rosterSize = roster.size)
            }?.let { result = result.copy(missingOnServer = result.missingOnServer || it) }
        }
        step("맞춤 설정") {
            var saved = 0
            store.dirtyComforts().forEach { driver ->
                // 조직에서 빠진 운전자(false)도 더 보내지 않는다
                if (auth.withToken { api.saveComfort(it, SaveComfortRequest(driver.id, driver.toRemoteComfort())) }) saved++
                store.markComfortSynced(driver.id, driver.updatedAt)
            }
            result = result.copy(comfortsSaved = saved)
        }?.let { result = result.copy(missingOnServer = result.missingOnServer || it) }
        store.pendingStatus?.let { status ->
            step("차량 상태") {
                auth.withToken { api.reportStatus(it, ReportStatusRequest(status)) }
                if (store.pendingStatus == status) store.pendingStatus = null
                result = result.copy(statusSent = true)
            }?.let { result = result.copy(missingOnServer = result.missingOnServer || it) }
        }
        return result
    }

    /** 실패하면 로그만 남긴다. 서버에 함수가 없으면(0010 미적용, 404) true */
    private suspend fun step(name: String, block: suspend () -> Unit): Boolean? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$name 동기화 실패: ${e.message}")
        (e as? com.altrostudio.drivelog.data.remote.BackendException)?.httpCode == HTTP_NOT_FOUND
    }

    companion object {
        private const val TAG = "DriveLog/CabinSync"
        private const val HTTP_NOT_FOUND = 404

        /** 운전자 명단을 다시 받는 간격 (관리자가 웹에서 운전자를 추가해도 이 안에 차에 보인다) */
        const val ROSTER_TTL_MS = 10 * 60_000L
    }
}

fun RemoteDriver.toEntity(order: Int, at: Long) = DriverEntity(
    id = id,
    name = name,
    department = department,
    comfortTempC = comfort?.tempC,
    comfortSeatHeat = comfort?.seatHeat,
    comfortAmbientColor = comfort?.ambientColor?.let(::parseRgb),
    assigned = assigned,
    isDefault = isDefault,
    sortOrder = order,
    comfortDirty = false,
    updatedAt = at
)

fun DriverEntity.toRemoteComfort() = RemoteComfort(
    tempC = comfortTempC,
    seatHeat = comfortSeatHeat,
    ambientColor = comfortAmbientColor?.let(::formatRgb)
)

/** "#RRGGBB" → 0xRRGGBB. 형식이 다르면 null */
fun parseRgb(text: String): Int? =
    text.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)

fun formatRgb(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)
