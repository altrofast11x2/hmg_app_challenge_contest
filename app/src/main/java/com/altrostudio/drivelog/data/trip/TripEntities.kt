package com.altrostudio.drivelog.data.trip

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.altrostudio.drivelog.data.navi.NaviArrival
import com.altrostudio.drivelog.data.vehicle.EnergyType

/**
 * 운행 한 건. 계기판·시각·배터리 값은 차량에서 읽은 원본이고 바꾸지 않는다.
 * 완료되면 [chainSeq]/[prevHash]/[hash]로 차량별 해시 체인에 들어간다.
 */
@Entity(
    tableName = "trips",
    indices = [
        Index("status"),
        Index("startedAt"),
        Index(value = ["vehicleId", "chainSeq"], unique = true)
    ]
)
data class TripEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    /** 차량을 조직에 연결한 뒤 채운다 */
    val orgId: String? = null,
    val driverId: String? = null,
    val status: TripStatus,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** 마지막으로 주행 중임을 확인한 시각. 앱이 주행 중 강제 종료됐을 때 종료 시각으로 쓴다 */
    val lastSeenAt: Long,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val odometerSource: ValueSource = ValueSource.VEHICLE,
    val startSocPercent: Int? = null,
    val endSocPercent: Int? = null,
    /** 차량이 지원할 때만 기록한다 */
    val efficiency: Double? = null,
    val efficiencyType: EnergyType? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    /** 운행기록부 비고(방문처) */
    val visitNote: String? = null,
    /** 현재 분류. 분류·재분류는 모두 trip_changes에 이력으로 남는다 */
    val purpose: TripPurpose = TripPurpose.UNCLASSIFIED,
    val chainSeq: Long? = null,
    val prevHash: String? = null,
    val hash: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val updatedAt: Long
)

/** 주행 중 도착한 경유지·목적지 */
@Entity(
    tableName = "trip_stops",
    indices = [Index("tripId")],
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class TripStopEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val kind: NaviArrival.Kind,
    val name: String,
    val address: String,
    val arrivedAt: Long
)

/** 분류·수정 이력. 지우거나 고치지 않고 계속 쌓으며, 운행과 같은 차량별 해시 체인에 들어간다 */
@Entity(
    tableName = "trip_changes",
    indices = [Index("tripId"), Index(value = ["vehicleId", "chainSeq"], unique = true)],
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"])]
)
data class TripChangeEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val vehicleId: String,
    val field: ChangeField,
    val oldValue: String?,
    val newValue: String?,
    /** 바꾼 사람. 예: "driver:default", "admin:<id>" */
    val actor: String,
    val reason: String? = null,
    val createdAt: Long,
    val chainSeq: Long,
    val prevHash: String,
    val hash: String,
    val syncState: SyncState = SyncState.PENDING
)

/** 차량별 해시 체인의 마지막 위치. 끝부분을 몰래 지운 것도 찾아낼 수 있게 따로 둔다 */
@Entity(tableName = "chain_heads")
data class ChainHeadEntity(
    @PrimaryKey val vehicleId: String,
    val seq: Long,
    val hash: String
)

fun TripEntity.distanceKm(): Double? {
    val start = startOdometerKm ?: return null
    val end = endOdometerKm ?: return null
    return end - start
}

/** 운행을 마친다. 비고(방문처)는 내비 목적지 이름, 없으면 종료 주소 */
fun TripEntity.completed(at: Long, destinationName: String?, updatedAt: Long): TripEntity = copy(
    status = TripStatus.COMPLETED,
    endedAt = endedAt ?: at,
    visitNote = destinationName?.takeIf { it.isNotBlank() } ?: endAddress,
    updatedAt = updatedAt
)

/** 분류를 바꾼다. 비업무 운행은 주소·위치를 남기지 않고 거리만 남긴다 */
fun TripEntity.classifiedAs(purpose: TripPurpose, updatedAt: Long): TripEntity {
    val classified = copy(purpose = purpose, syncState = SyncState.PENDING, updatedAt = updatedAt)
    return if (purpose == TripPurpose.PERSONAL) {
        classified.copy(startAddress = null, endAddress = null, visitNote = null)
    } else {
        classified
    }
}
