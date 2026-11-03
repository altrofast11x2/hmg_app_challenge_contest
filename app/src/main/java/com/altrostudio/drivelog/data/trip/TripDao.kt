package com.altrostudio.drivelog.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE status != 'COMPLETED' ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeTrip(): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status = 'COMPLETED' AND startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    fun observeCompletedTrips(from: Long, to: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status != 'COMPLETED' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveTrip(): Flow<TripEntity?>

    @Query(
        "SELECT purpose FROM trips WHERE status = 'COMPLETED' AND endAddress = :address " +
            "AND purpose IN ('COMMUTE', 'BUSINESS') ORDER BY startedAt DESC LIMIT 1"
    )
    suspend fun lastWorkPurposeAt(address: String): TripPurpose?

    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId AND chainSeq IS NOT NULL ORDER BY chainSeq")
    suspend fun sealedTrips(vehicleId: String): List<TripEntity>

    @Insert
    suspend fun insertStop(stop: TripStopEntity)

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY arrivedAt")
    suspend fun stops(tripId: String): List<TripStopEntity>

    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    suspend fun deleteStops(tripId: String)

    @Query("DELETE FROM trips WHERE id = :tripId AND status != 'COMPLETED'")
    suspend fun deleteUnfinishedTrip(tripId: String)

    @Insert
    suspend fun insertChange(change: TripChangeEntity)

    @Update
    suspend fun updateChange(change: TripChangeEntity)

    @Query("SELECT * FROM trip_changes WHERE tripId = :tripId ORDER BY chainSeq")
    suspend fun changesForTrip(tripId: String): List<TripChangeEntity>

    @Query("SELECT * FROM trip_changes WHERE vehicleId = :vehicleId ORDER BY chainSeq")
    suspend fun changes(vehicleId: String): List<TripChangeEntity>

    @Query("SELECT * FROM chain_heads WHERE vehicleId = :vehicleId")
    suspend fun chainHead(vehicleId: String): ChainHeadEntity?

    @Upsert
    suspend fun upsertChainHead(head: ChainHeadEntity)

    @Query("SELECT * FROM chain_heads")
    suspend fun chainHeads(): List<ChainHeadEntity>

    /** 그 차량 체인의 첫 기록(seq 1) 해시. 첫 기록은 운행이다 */
    @Query(
        "SELECT hash FROM trips WHERE vehicleId = :vehicleId AND chainSeq = 1 " +
            "UNION ALL SELECT hash FROM trip_changes WHERE vehicleId = :vehicleId AND chainSeq = 1 LIMIT 1"
    )
    suspend fun chainStartHash(vehicleId: String): String?

    @Query(
        "SELECT * FROM trips WHERE status = 'COMPLETED' AND syncState = 'PENDING' AND chainSeq IS NOT NULL " +
            "ORDER BY chainSeq LIMIT :limit"
    )
    suspend fun pendingTrips(limit: Int): List<TripEntity>

    @Query("SELECT * FROM trip_changes WHERE syncState = 'PENDING' ORDER BY chainSeq LIMIT :limit")
    suspend fun pendingChanges(limit: Int): List<TripChangeEntity>

    /** 올리는 동안 바뀌지 않았을 때만(updatedAt이 같을 때만) 올린 것으로 표시한다 */
    @Query("UPDATE trips SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markTripSynced(id: String, updatedAt: Long)

    @Query("UPDATE trip_changes SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markChangesSynced(ids: List<String>)

    @Query(
        "SELECT (SELECT COUNT(*) FROM trips WHERE status = 'COMPLETED' AND syncState = 'PENDING') + " +
            "(SELECT COUNT(*) FROM trip_changes WHERE syncState = 'PENDING')"
    )
    fun observePendingCount(): Flow<Int>

    /** 운전자를 고른 운행은 그 운전자를 그대로 둔다 */
    @Query("UPDATE trips SET orgId = :orgId, driverId = COALESCE(driverId, :driverId) WHERE orgId IS NULL")
    suspend fun assignOrg(orgId: String, driverId: String?)

    @Query("SELECT * FROM trips WHERE status = 'COMPLETED' ORDER BY endedAt DESC, startedAt DESC LIMIT 1")
    suspend fun latestCompletedTrip(): TripEntity?

    @Query(
        "UPDATE trip_changes SET syncState = 'SYNCED' WHERE syncState = 'PENDING' " +
            "AND tripId IN (SELECT id FROM trips WHERE orgId IS NOT NULL AND orgId != :orgId)"
    )
    suspend fun retireChangesOfOtherOrgs(orgId: String)

    @Query("UPDATE trips SET syncState = 'SYNCED' WHERE syncState = 'PENDING' AND orgId IS NOT NULL AND orgId != :orgId")
    suspend fun retireTripsOfOtherOrgs(orgId: String)
}
