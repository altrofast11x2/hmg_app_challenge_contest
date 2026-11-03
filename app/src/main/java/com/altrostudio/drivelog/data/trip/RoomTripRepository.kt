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
            TripLedger.completionChanges(sealed, at, newId).forEach {
                dao.insertChange(it)
                dao.upsertChainHead(TripLedger.headOf(it))
            }
        }
    }

    override suspend fun setDriver(tripId: String, driver: DriverRef?, actor: String, at: Long) {
        database.withTransaction {
            val trip = dao.trip(tripId) ?: return@withTransaction
            val updated = trip.withDriver(driver, now())
            if (updated.driverLabel() == trip.driverLabel() && updated.driverId == trip.driverId) return@withTransaction
            if (trip.status == TripStatus.COMPLETED) {
                val change = TripLedger.newChange(
                    newId(), trip, ChangeField.DRIVER, trip.driverLabel(), updated.driverLabel(), actor, at, dao.chainHead(trip.vehicleId)
                )
                dao.insertChange(change)
                dao.upsertChainHead(TripLedger.headOf(change))
            }
            dao.updateTrip(updated)
        }
    }

    override suspend fun setPlan(tripId: String, purpose: TripPurpose?, workNote: String?) = update(tripId) {
        if (it.status == TripStatus.COMPLETED) {
            it
        } else {
            it.copy(
                plannedPurpose = purpose?.takeIf { p -> p != TripPurpose.UNCLASSIFIED },
                workNote = if (purpose == TripPurpose.PERSONAL) null else normalizedWorkNote(workNote) ?: it.workNote
            )
        }
    }

    override suspend fun setWorkNote(tripId: String, note: String?, actor: String, at: Long): Boolean =
        database.withTransaction {
            val trip = dao.trip(tripId) ?: return@withTransaction false
            check(trip.status == TripStatus.COMPLETED) { "완료된 운행만 업무 내용을 적을 수 있습니다" }
            if (trip.purpose == TripPurpose.PERSONAL) return@withTransaction false
            val clean = normalizedWorkNote(note)
            if (clean == trip.workNote) return@withTransaction true
            val change = TripLedger.newChange(
                newId(), trip, ChangeField.WORK_NOTE, trip.workNote, clean, actor, at, dao.chainHead(trip.vehicleId)
            )
            dao.insertChange(change)
            dao.upsertChainHead(TripLedger.headOf(change))
            dao.updateTrip(trip.copy(workNote = clean, syncState = SyncState.PENDING, updatedAt = now()))
            true
        }

    override suspend fun latestCompletedTrip(): TripEntity? = dao.latestCompletedTrip()

    override suspend fun discardTrip(tripId: String) {
        database.withTransaction {
            val trip = dao.trip(tripId) ?: return@withTransaction
            if (trip.status == TripStatus.COMPLETED) return@withTransaction
            dao.deleteStops(tripId)
            dao.deleteUnfinishedTrip(tripId)
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

    override suspend fun pendingUploads(limit: Int): PendingUploads =
        PendingUploads(dao.pendingTrips(limit), dao.pendingChanges(limit))

    override suspend fun markUploaded(trips: List<TripEntity>, changes: List<TripChangeEntity>) {
        database.withTransaction {
            trips.forEach { dao.markTripSynced(it.id, it.updatedAt) }
            if (changes.isNotEmpty()) dao.markChangesSynced(changes.map { it.id })
        }
    }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override suspend fun assignOrg(orgId: String, driverId: String?) {
        database.withTransaction {
            dao.retireChangesOfOtherOrgs(orgId)
            dao.retireTripsOfOtherOrgs(orgId)
            dao.assignOrg(orgId, driverId)
        }
    }

    override suspend fun chainHeads(): List<ChainHeadEntity> = dao.chainHeads()

    override suspend fun chainId(vehicleId: String): String? = dao.chainStartHash(vehicleId)

    private suspend fun update(tripId: String, change: (TripEntity) -> TripEntity) {
        database.withTransaction {
            val trip = dao.trip(tripId) ?: return@withTransaction
            val updated = change(trip)
            if (updated != trip) dao.updateTrip(updated.copy(updatedAt = now()))
        }
    }
}
