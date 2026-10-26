package com.altrostudio.drivelog.data.trip

import com.altrostudio.drivelog.data.trip.TripLedger.redacted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLedgerTest {

    private fun completedTrip(id: String, startKm: Double, endKm: Double, startedAt: Long) = TripEntity(
        id = id,
        vehicleId = "V1",
        status = TripStatus.COMPLETED,
        startedAt = startedAt,
        endedAt = startedAt + 600_000,
        lastSeenAt = startedAt + 600_000,
        startOdometerKm = startKm,
        endOdometerKm = endKm,
        startAddress = "출발지",
        endAddress = "도착지",
        updatedAt = startedAt
    )

    private class Chain(val trips: List<TripEntity>, val changes: List<TripChangeEntity>, val head: ChainHeadEntity)

    /** 운행1(seq 1) → 운행1 일반업무 분류(seq 2) → 운행2(seq 3) */
    private fun buildChain(): Chain {
        val trip1 = TripLedger.sealTrip(completedTrip("t1", 100.0, 112.3, 1_000), head = null)
        val change = TripLedger.newChange(
            "c1", trip1, ChangeField.PURPOSE, "UNCLASSIFIED", "BUSINESS", "driver:default", 2_000, TripLedger.headOf(trip1)
        )
        val trip2 = TripLedger.sealTrip(completedTrip("t2", 112.3, 130.0, 3_000), TripLedger.headOf(change))
        return Chain(
            trips = listOf(trip1.copy(purpose = TripPurpose.BUSINESS), trip2),
            changes = listOf(change),
            head = TripLedger.headOf(trip2)
        )
    }

    private fun Chain.verify(
        trips: List<TripEntity> = this.trips,
        changes: List<TripChangeEntity> = this.changes,
        head: ChainHeadEntity? = this.head
    ) = TripLedger.verify(trips, changes, head)

    @Test
    fun `손대지 않은 체인은 검증을 통과한다`() {
        assertEquals(ChainVerification.Valid(3), buildChain().verify())
    }

    @Test
    fun `계기판 거리를 고치면 그 기록에서 끊긴다`() {
        val chain = buildChain()
        val tampered = chain.trips.map { if (it.id == "t1") it.copy(endOdometerKm = 150.0) else it }
        val result = chain.verify(trips = tampered)
        assertEquals(1L, (result as ChainVerification.Broken).atSeq)
    }

    @Test
    fun `분류 이력을 고치면 그 이력에서 끊긴다`() {
        val chain = buildChain()
        val tampered = chain.changes.map { it.copy(newValue = "COMMUTE") }
        val result = chain.verify(changes = tampered)
        assertEquals(2L, (result as ChainVerification.Broken).atSeq)
    }

    @Test
    fun `이력 없이 운행의 분류만 바꾸면 끊긴다`() {
        val chain = buildChain()
        val tampered = chain.trips.map { if (it.id == "t2") it.copy(purpose = TripPurpose.BUSINESS) else it }
        val result = chain.verify(trips = tampered)
        assertEquals(3L, (result as ChainVerification.Broken).atSeq)
    }

    @Test
    fun `중간 기록을 지우면 끊긴다`() {
        val chain = buildChain()
        val result = chain.verify(trips = chain.trips.map { it.copy(purpose = TripPurpose.UNCLASSIFIED) }, changes = emptyList())
        assertEquals(2L, (result as ChainVerification.Broken).atSeq)
    }

    @Test
    fun `마지막 기록을 지우면 체인 머리와 맞지 않아 끊긴다`() {
        val chain = buildChain()
        val result = chain.verify(trips = chain.trips.filter { it.id != "t2" })
        assertTrue(result is ChainVerification.Broken)
    }

    @Test
    fun `비업무로 주소를 지워도 체인은 유지된다`() {
        val chain = buildChain()
        val withoutAddress = chain.trips.map { it.copy(startAddress = null, endAddress = null, visitNote = null) }
        assertEquals(ChainVerification.Valid(3), chain.verify(trips = withoutAddress))
    }

    @Test
    fun `위치 수정 이력의 원문을 가려도 체인은 유지되고, 가린 값을 바꾸면 끊긴다`() {
        val trip = TripLedger.sealTrip(completedTrip("t1", 100.0, 110.0, 1_000), head = null)
        val visit = TripLedger.newChange(
            "c1", trip, ChangeField.VISIT_NOTE, "도착지", "고객사 A", "driver:default", 2_000, TripLedger.headOf(trip)
        )
        val head = TripLedger.headOf(visit)

        val redacted = visit.redacted()
        assertTrue(redacted.newValue!!.startsWith("sha256:"))
        assertEquals(ChainVerification.Valid(2), TripLedger.verify(listOf(trip), listOf(redacted), head))

        val forged = redacted.copy(newValue = HashChain.commitment("고객사 B"))
        assertTrue(TripLedger.verify(listOf(trip), listOf(forged), head) is ChainVerification.Broken)
    }
}
