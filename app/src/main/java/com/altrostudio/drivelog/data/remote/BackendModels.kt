package com.altrostudio.drivelog.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 서버 주소와 공개(anon) 키. 둘 중 하나라도 비어 있으면 서버 없이 이 차량 안에서만 동작한다 */
data class SupabaseConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}

/** 차량 앱의 익명 로그인 세션 */
@Serializable
data class DeviceSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val userId: String
)

@Serializable
internal data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long,
    val user: AuthUser
)

@Serializable
internal data class AuthUser(val id: String)

@Serializable
internal data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class PairRequest(
    @SerialName("p_code") val code: String,
    @SerialName("p_vehicle_id") val vehicleId: String,
    @SerialName("p_vin") val vin: String?
)

/** pair_vehicle 함수의 결과. status: OK, INVALID_CODE, EXPIRED_CODE, TOO_MANY_ATTEMPTS, VEHICLE_IN_OTHER_ORG, UNAUTHENTICATED */
@Serializable
data class PairResponse(
    val status: String,
    @SerialName("org_id") val orgId: String? = null,
    @SerialName("org_name") val orgName: String? = null,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("driver_name") val driverName: String? = null
)

/** 서버 trips 테이블의 한 줄 (backend/supabase/migrations/0001_init.sql) */
@Serializable
data class RemoteTrip(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("driver_id") val driverId: String?,
    @SerialName("started_at_ms") val startedAtMs: Long,
    @SerialName("ended_at_ms") val endedAtMs: Long?,
    @SerialName("start_odometer_km") val startOdometerKm: Double?,
    @SerialName("end_odometer_km") val endOdometerKm: Double?,
    @SerialName("odometer_source") val odometerSource: String,
    @SerialName("start_soc_percent") val startSocPercent: Int?,
    @SerialName("end_soc_percent") val endSocPercent: Int?,
    val efficiency: Double?,
    @SerialName("efficiency_type") val efficiencyType: String?,
    @SerialName("start_address") val startAddress: String?,
    @SerialName("end_address") val endAddress: String?,
    @SerialName("visit_note") val visitNote: String?,
    val purpose: String,
    @SerialName("chain_seq") val chainSeq: Long,
    @SerialName("prev_hash") val prevHash: String,
    val hash: String
)

@Serializable
data class RemoteTripChange(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("vehicle_id") val vehicleId: String,
    val field: String,
    @SerialName("old_value") val oldValue: String?,
    @SerialName("new_value") val newValue: String?,
    val actor: String,
    val reason: String?,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("chain_seq") val chainSeq: Long,
    @SerialName("prev_hash") val prevHash: String,
    val hash: String
)

@Serializable
data class RemoteChainHead(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("org_id") val orgId: String,
    val seq: Long,
    val hash: String
)
