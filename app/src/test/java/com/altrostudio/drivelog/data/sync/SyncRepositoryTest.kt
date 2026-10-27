package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.trip.SyncState
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.fake.FakeTripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryTest {

    private var clock = 0L
    private val api = FakeBackendApi()
    private val pairing = MutableStateFlow<Pairing?>(null)
    private val trips = FakeTripRepository(now = { ++clock })
    private val sync = SyncRepository(api, DeviceAuth(api, MemorySyncSettings(), now = { 0L }), pairing, trips)

    private val demoOrg = Pairing(orgId = "org-1", orgName = "데모 조직", driverId = "driver-1", driverName = "기본 운전자")

    private suspend fun completedTrip(id: String, purpose: TripPurpose? = null) {
        trips.startTrip(id, VEHICLE_ID, at = 1_000, odometerKm = 100.0)
        trips.updateStart(id, socPercent = 80, address = "서울 강남구")
        trips.markParked(id, at = 2_000, odometerKm = 112.3)
        trips.updateEnd(id, socPercent = 75, address = "경기 성남시", efficiency = null)
        trips.completeTrip(id, at = 2_000)
        purpose?.let { trips.classify(id, it, "driver:default", at = 3_000) }
    }

    @Test
    fun `조직에 연결하기 전에는 아무것도 올리지 않는다`() = runTest {
        completedTrip("t1")
        assertEquals(SyncOutcome.NotPaired, sync.sync())
        assertTrue(api.trips.isEmpty())
    }

    @Test
    fun `연결되면 완료 운행과 분류 이력을 조직·운전자를 채워 올리고 올린 것으로 표시한다`() = runTest {
        completedTrip("t1", TripPurpose.BUSINESS)
        pairing.value = demoOrg

        assertEquals(SyncOutcome.Uploaded(trips = 1, changes = 1), sync.sync())

        val remote = api.trips.getValue("t1")
        assertEquals("org-1", remote.orgId)
        assertEquals("driver-1", remote.driverId)
        assertEquals("BUSINESS", remote.purpose)
        assertEquals(112.3, remote.endOdometerKm!!, 0.001)
        assertEquals(1L, remote.chainSeq)
        assertEquals(1, api.changes.size)
        assertEquals(2L, api.chainHeads.getValue(VEHICLE_ID).seq)
        assertEquals(SyncState.SYNCED, trips.trip("t1").syncState)
        assertEquals("org-1", trips.trip("t1").orgId)
        assertEquals(SyncOutcome.UpToDate, sync.sync())
    }

    @Test
    fun `동의 철회 뒤 같은 차에서 새로 시작한 체인은 체인 ID(첫 기록의 해시)로 옛 체인과 구분해 올린다`() = runTest {
        completedTrip("t1", TripPurpose.BUSINESS)
        pairing.value = demoOrg
        sync.sync()
        val firstChain = checkNotNull(trips.trip("t1").hash)
        assertEquals(firstChain, api.trips.getValue("t1").chainId)
        assertEquals(listOf(firstChain), api.changes.values.map { it.chainId })
        assertEquals(firstChain, api.chainHeads.getValue(VEHICLE_ID).chainId)

        // 동의 철회로 차 안 기록이 지워진 뒤 다시 연결해 운행
        trips.deleteAll()
        completedTrip("t2", TripPurpose.COMMUTE)
        assertEquals(SyncOutcome.Uploaded(trips = 1, changes = 1), sync.sync())

        val second = api.trips.getValue("t2")
        assertEquals(1L, second.chainSeq)
        assertEquals(trips.trip("t2").hash, second.chainId)
        assertNotEquals(firstChain, second.chainId)
        assertEquals(second.chainId, api.chainHeads.getValue(VEHICLE_ID).chainId)
    }

    @Test
    fun `진행 중인 운행은 올리지 않는다`() = runTest {
        trips.startTrip("t1", VEHICLE_ID, at = 1_000, odometerKm = 100.0)
        pairing.value = demoOrg
        assertEquals(SyncOutcome.UpToDate, sync.sync())
        assertTrue(api.trips.isEmpty())
    }

    @Test
    fun `인터넷이 없으면 기록을 그대로 두고 다음에 다시 올린다`() = runTest {
        completedTrip("t1")
        pairing.value = demoOrg
        api.failure = BackendException(BackendException.Kind.NETWORK, "offline")

        assertEquals(SyncOutcome.Offline, sync.sync())
        assertEquals(SyncState.PENDING, trips.trip("t1").syncState)

        api.failure = null
        assertEquals(SyncOutcome.Uploaded(trips = 1, changes = 0), sync.sync())
    }

    @Test
    fun `네트워크 오류가 아닌 예외에도 멈추지 않고 기록을 남겨 다음에 다시 올린다`() = runTest {
        completedTrip("t1")
        pairing.value = demoOrg
        api.failure = SecurityException("Permission denied (missing INTERNET permission?)")

        assertTrue(sync.sync() is SyncOutcome.Failed)
        assertEquals(SyncState.PENDING, trips.trip("t1").syncState)
    }

    @Test
    fun `토큰이 거부되면 새 토큰을 받아 한 번 더 시도한다`() = runTest {
        completedTrip("t1")
        pairing.value = demoOrg
        api.rejectTokenOnce = true

        assertEquals(SyncOutcome.Uploaded(trips = 1, changes = 0), sync.sync())
        assertEquals("access-2", api.usedTokens.first())
    }

    @Test
    fun `올린 뒤 비업무로 다시 분류하면 주소를 지운 상태로 다시 올라간다`() = runTest {
        completedTrip("t1")
        pairing.value = demoOrg
        sync.sync()
        assertEquals("서울 강남구", api.trips.getValue("t1").startAddress)

        trips.classify("t1", TripPurpose.PERSONAL, "driver:default", at = 4_000)
        assertEquals(SyncOutcome.Uploaded(trips = 1, changes = 1), sync.sync())

        val remote = api.trips.getValue("t1")
        assertEquals("PERSONAL", remote.purpose)
        assertNull(remote.startAddress)
        assertNull(remote.endAddress)
        assertEquals(1, api.trips.size)
    }

    private companion object {
        const val VEHICLE_ID = "VIN-TEST"
    }
}
