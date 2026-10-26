package com.altrostudio.drivelog.data.trip

import androidx.room.withTransaction
import com.altrostudio.drivelog.data.DriveLogDatabase
import com.altrostudio.drivelog.data.navi.NaviArrival
import com.altrostudio.drivelog.data.trip.TripLedger.redacted
import com.altrostudio.drivelog.data.vehicle.EnergyEfficiency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomTripRepository(
    private val database: DriveLogDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : TripRepository {

    private val dao = database.tripDao()

    override suspend fun activeTrip(): TripEntity? = dao.activeTrip()

    override suspend fun startTrip(tripId: String, vehicleId: String, at: Long, odometerKm: Double?) {
        dao.insertTrip(
            TripEntity(
                id = tripId,
                vehicleId = vehicleId,
                status = TripStatus.DRIVING,
                startedAt = at,
                lastSeenAt = at,
                startOdometerKm = odometerKm,
                updatedAt = now()
            )
        )
    }

    override suspend fun updateStart(tripId: String, socPercent: Int?, address: String?) = update(tripId) {
        if (it.status == TripStatus.COMPLETED) it else it.copy(startSocPercent = socPercent, startAddress = address)
    }

    override suspend fun touch(tripId: String, at: Long) = update(tripId) {
        if (it.status == TripStatus.DRIVING) it.copy(lastSeenAt = at) else it
    }

    override suspend fun markParked(tripId: String, at: Long, odometerKm: Double?) = update(tripId) {
        it.copy(status = TripStatus.PARKED, endedAt = at, lastSeenAt = at, endOdometerKm = odometerKm)
    }

    override suspend fun updateEnd(tripId: String, socPercent: Int?, address: String?, efficiency: EnergyEfficiency?) =
        update(tripId) {
            if (it.status != TripStatus.PARKED) {
                it
            } else {
                it.copy(
                    endSocPercent = socPercent,
                    endAddress = address,
                    efficiency = efficiency?.value?.toDouble(),
                    efficiencyType = efficiency?.type
                )
            }
        }

    override suspend fun resumeDriving(tripId: String, at: Long) = update(tripId) {
        it.copy(
            status = TripStatus.DRIVING,
            endedAt = null,
            lastSeenAt = at,
            endOdometerKm = null,
            endSocPercent = null,
            endAddress = null,
            efficiency = null,
            efficiencyType = null
        )
    }

    override suspend fun addStop(tripId: String, arrival: NaviArrival, at: Long) {
        dao.insertStop(TripStopEntity(newId(), tripId, arrival.kind, arrival.name, arrival.address, at))
    }

    override suspend fun completeTrip(tripId: String, at: Long) {
        database.withTransaction {
            val trip = dao.trip(tripId) ?: return@withTransaction
            if (trip.status == TripStatus.COMPLETED) return@withTransaction
            val destination = dao.stops(tripId).lastOrNull { it.kind == NaviArrival.Kind.DESTINATION }?.name
            val sealed = TripLedger.sealTrip(trip.completed(at, destination, now()), dao.chainHead(trip.vehicleId))
            dao.updateTrip(sealed)
            dao.upsertChainHead(TripLedger.headOf(sealed))
        }
    }

    override suspend fun classify(tripId: String, purpose: TripPurpose, actor: String, at: Long) {
        require(purpose != TripPurpose.UNCLASSIFIED) { "분류는 출퇴근·일반업무·비업무 중 하나여야 합니다" }
        database.withTransaction {
            val trip = dao.trip(tripId) ?: return@withTransaction
            check(trip.status == TripStatus.COMPLETED) { "완료된 운행만 분류할 수 있습니다" }
            if (trip.purpose == purpose) return@withTransaction
            val change = TripLedger.newChange(
                newId(), trip, ChangeField.PURPOSE, trip.purpose.name, purpose.name, actor, at, dao.chainHead(trip.vehicleId)
            )
            dao.insertChange(change)
            dao.upsertChainHead(TripLedger.headOf(change))
            dao.updateTrip(trip.classifiedAs(purpose, now()))
            if (purpose == TripPurpose.PERSONAL) {
                dao.deleteStops(tripId)
                dao.changesForTrip(tripId).filter { it.field.isLocation }.forEach { dao.updateChange(it.redacted()) }
            }
        }
    }

    override fun observeTrips(): Flow<List<TripEntity>> = dao.observeTrips()

    override fun observeCompletedTrips(from: Long, to: Long): Flow<List<TripEntity>> = dao.observeCompletedTrips(from, to)

    override fun observeActiveTrip(): Flow<TripEntity?> = dao.observeActiveTrip()

    override suspend fun suggestPurpose(address: String): TripPurpose? = dao.lastWorkPurposeAt(address)

    override suspend fun verifyChain(vehicleId: String): ChainVerification =
        TripLedger.verify(dao.sealedTrips(vehicleId), dao.changes(vehicleId), dao.chainHead(vehicleId))

    override suspend fun deleteAll() = withContext(Dispatchers.IO) { database.clearAllTables() }

    private suspend fun update(tripId: String, change: (TripEntity) -> TripEntity) {
        database.withTransaction {
            val trip = dao.trip(tripId) ?: return@withTransaction
            val updated = change(trip)
            if (updated != trip) dao.updateTrip(updated.copy(updatedAt = now()))
        }
    }
}
