package com.altrostudio.drivelog.fake

import com.altrostudio.drivelog.data.navi.NaviArrival
import com.altrostudio.drivelog.data.trip.ChainHeadEntity
import com.altrostudio.drivelog.data.trip.ChainVerification
import com.altrostudio.drivelog.data.trip.ChangeField
import com.altrostudio.drivelog.data.trip.TripChangeEntity
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripLedger
import com.altrostudio.drivelog.data.trip.TripLedger.redacted
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripRepository
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.data.trip.TripStopEntity
import com.altrostudio.drivelog.data.trip.classifiedAs
import com.altrostudio.drivelog.data.trip.completed
import com.altrostudio.drivelog.data.vehicle.EnergyEfficiency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** debug 빌드·단위 테스트 전용 메모리 저장소. 해시 체인·비업무 규칙은 실제 저장소와 같은 함수를 쓴다 */
class FakeTripRepository(
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : TripRepository {

    private val mutex = Mutex()
    val trips = MutableStateFlow<List<TripEntity>>(emptyList())
    val stops = mutableListOf<TripStopEntity>()
    val changes = mutableListOf<TripChangeEntity>()
    val heads = mutableMapOf<String, ChainHeadEntity>()

    fun trip(id: String): TripEntity = trips.value.first { it.id == id }

    override suspend fun activeTrip(): TripEntity? = mutex.withLock {
        trips.value.lastOrNull { it.status != TripStatus.COMPLETED }
    }

    override suspend fun startTrip(tripId: String, vehicleId: String, at: Long, odometerKm: Double?) = mutex.withLock {
        trips.value += TripEntity(
            id = tripId,
            vehicleId = vehicleId,
            status = TripStatus.DRIVING,
            startedAt = at,
            lastSeenAt = at,
            startOdometerKm = odometerKm,
            updatedAt = now()
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

    override suspend fun addStop(tripId: String, arrival: NaviArrival, at: Long) = mutex.withLock {
        stops += TripStopEntity(newId(), tripId, arrival.kind, arrival.name, arrival.address, at)
        Unit
    }

    override suspend fun completeTrip(tripId: String, at: Long) = mutex.withLock {
        val trip = trips.value.firstOrNull { it.id == tripId } ?: return@withLock
        if (trip.status == TripStatus.COMPLETED) return@withLock
        val destination = stops.lastOrNull { it.tripId == tripId && it.kind == NaviArrival.Kind.DESTINATION }?.name
        val sealed = TripLedger.sealTrip(trip.completed(at, destination, now()), heads[trip.vehicleId])
        replace(sealed)
        heads[trip.vehicleId] = TripLedger.headOf(sealed)
    }

    override suspend fun classify(tripId: String, purpose: TripPurpose, actor: String, at: Long) {
        require(purpose != TripPurpose.UNCLASSIFIED)
        mutex.withLock {
            val trip = trips.value.firstOrNull { it.id == tripId } ?: return@withLock
            check(trip.status == TripStatus.COMPLETED)
            if (trip.purpose == purpose) return@withLock
            val change = TripLedger.newChange(
                newId(), trip, ChangeField.PURPOSE, trip.purpose.name, purpose.name, actor, at, heads[trip.vehicleId]
            )
            changes += change
            heads[trip.vehicleId] = TripLedger.headOf(change)
            replace(trip.classifiedAs(purpose, now()))
            if (purpose == TripPurpose.PERSONAL) {
                stops.removeAll { it.tripId == tripId }
                changes.replaceAll { if (it.tripId == tripId) it.redacted() else it }
            }
        }
    }

    override fun observeTrips(): Flow<List<TripEntity>> = trips.map { list -> list.sortedByDescending { it.startedAt } }

    override fun observeCompletedTrips(from: Long, to: Long): Flow<List<TripEntity>> = trips.map { list ->
        list.filter { it.status == TripStatus.COMPLETED && it.startedAt >= from && it.startedAt < to }.sortedBy { it.startedAt }
    }

    override fun observeActiveTrip(): Flow<TripEntity?> = trips.map { list ->
        list.filter { it.status != TripStatus.COMPLETED }.maxByOrNull { it.startedAt }
    }

    override suspend fun suggestPurpose(address: String): TripPurpose? = mutex.withLock {
        trips.value
            .filter { it.status == TripStatus.COMPLETED && it.endAddress == address }
            .filter { it.purpose == TripPurpose.COMMUTE || it.purpose == TripPurpose.BUSINESS }
            .maxByOrNull { it.startedAt }
            ?.purpose
    }

    override suspend fun deleteAll() = mutex.withLock {
        trips.value = emptyList()
        stops.clear()
        changes.clear()
        heads.clear()
    }

    override suspend fun verifyChain(vehicleId: String): ChainVerification = mutex.withLock {
        TripLedger.verify(
            trips.value.filter { it.vehicleId == vehicleId },
            changes.filter { it.vehicleId == vehicleId },
            heads[vehicleId]
        )
    }

    private fun replace(trip: TripEntity) {
        trips.value = trips.value.map { if (it.id == trip.id) trip else it }
    }

    private suspend fun update(tripId: String, change: (TripEntity) -> TripEntity) = mutex.withLock {
        trips.value = trips.value.map { trip ->
            if (trip.id != tripId) {
                trip
            } else {
                val updated = change(trip)
                if (updated == trip) trip else updated.copy(updatedAt = now())
            }
        }
    }
}
