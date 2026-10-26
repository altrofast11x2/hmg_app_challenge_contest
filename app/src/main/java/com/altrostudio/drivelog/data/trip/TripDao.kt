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

    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId AND chainSeq IS NOT NULL ORDER BY chainSeq")
    suspend fun sealedTrips(vehicleId: String): List<TripEntity>

    @Insert
    suspend fun insertStop(stop: TripStopEntity)

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY arrivedAt")
    suspend fun stops(tripId: String): List<TripStopEntity>

    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    suspend fun deleteStops(tripId: String)

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
}
