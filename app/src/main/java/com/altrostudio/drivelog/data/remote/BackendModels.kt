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

/** create_share_link 함수 인자: 휴대폰으로 받을 기간 [from, to) */
@Serializable
data class ShareLinkRequest(
    @SerialName("p_from_ms") val fromMs: Long,
    @SerialName("p_to_ms") val toMs: Long
)

/** create_share_link 함수의 결과. status: OK, NOT_PAIRED, INVALID_PERIOD, TOO_MANY */
@Serializable
data class ShareLinkResponse(
    val status: String,
    val token: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
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
    /** 체인 ID = 그 체인 첫 기록(seq 1)의 해시 (0009). 동의 철회·앱 재설치 뒤 같은 차에서 새로 시작한 체인과 구분한다 */
    @SerialName("chain_id") val chainId: String,
    @SerialName("chain_seq") val chainSeq: Long,
    @SerialName("prev_hash") val prevHash: String,
    val hash: String,
    /** 운행기록부 ④사용자 (운행 당시의 이름·부서, 0010) */
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("driver_department") val driverDepartment: String? = null,
    /** 업무 내용 (음성 업무 일지, 0010) */
    @SerialName("work_note") val workNote: String? = null
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
    @SerialName("chain_id") val chainId: String,
    @SerialName("chain_seq") val chainSeq: Long,
    @SerialName("prev_hash") val prevHash: String,
    val hash: String
)

@Serializable
data class RemoteChainHead(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("chain_id") val chainId: String,
    val seq: Long,
    val hash: String
)

/** device_driver_roster 함수의 한 줄 (0010): 이 차에서 고를 수 있는 운전자 */
@Serializable
data class RemoteDriver(
    val id: String,
    val name: String,
    val department: String? = null,
    val comfort: RemoteComfort? = null,
    val assigned: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false
)

/** 운전자 맞춤 설정. 서버는 알려진 항목·범위만 남긴다 (tempC 15~32, seatHeat -3~3, ambientColor "#RRGGBB") */
@Serializable
data class RemoteComfort(
    val tempC: Float? = null,
    val seatHeat: Int? = null,
    val ambientColor: String? = null
)

@Serializable
data class SaveComfortRequest(
    @SerialName("p_driver") val driverId: String,
    @SerialName("p_comfort") val comfort: RemoteComfort
)

/** device_report_status 함수 인자: 운행을 마쳤을 때의 차 상태 (관리자 웹 차량 현황) */
@Serializable
data class RemoteVehicleStatus(
    @SerialName("reported_at_ms") val reportedAtMs: Long,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("odometer_km") val odometerKm: Double? = null,
    @SerialName("soc_percent") val socPercent: Int? = null,
    @SerialName("open_windows") val openWindows: List<String> = emptyList(),
    @SerialName("doors_locked") val doorsLocked: Boolean? = null,
    @SerialName("cabin_light_on") val cabinLightOn: Boolean? = null,
    val tidied: List<String> = emptyList()
)

@Serializable
data class ReportStatusRequest(@SerialName("p_status") val status: RemoteVehicleStatus)
