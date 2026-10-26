package com.altrostudio.drivelog.data.settings

import android.util.Log
import com.altrostudio.drivelog.data.vehicle.VehicleDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 차량 ID. VIN이 있으면 VIN을, 없으면(에뮬레이터는 "invalid"를 준다) 처음 한 번 만든 기기 ID를 계속 쓴다.
 * 한 번 정한 ID는 바뀌지 않아야 해시 체인이 이어진다.
 */
class VehicleIdentity(
    private val preferences: AppPreferences,
    private val vehicle: VehicleDataSource
) {
    private val mutex = Mutex()

    suspend fun vehicleId(): String = mutex.withLock {
        preferences.vehicleId ?: run {
            val vin = try {
                vehicle.readVin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "VIN 조회 실패", e)
                null
            }
            val id = vin ?: "$DEVICE_ID_PREFIX${UUID.randomUUID()}"
            preferences.vin = vin
            preferences.vehicleId = id
            id
        }
    }

    companion object {
        private const val TAG = "DriveLog/Vehicle"
        const val DEVICE_ID_PREFIX = "device-"
    }
}
