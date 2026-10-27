package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.PairResponse
import com.altrostudio.drivelog.data.trip.SyncState
import com.altrostudio.drivelog.fake.FakeTripRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRepositoryTest {

    private val api = FakeBackendApi()
    private val settings = MemorySyncSettings(vin = null).apply { pairingSkipped = true }
    private val trips = FakeTripRepository(now = { 0L })
    private val repository = PairingRepository(
        api, DeviceAuth(api, settings, now = { 0L }), settings, vehicleId = { "device-1234" }, trips, isConfigured = true
    )

    @Test
    fun `동의를 철회하면 서버의 차량 연결도 끊고, 연결한 적이 없거나 인터넷이 없으면 철회를 막지 않는다`() = runTest {
        assertFalse(repository.disconnectFromServer())
        assertEquals(0, api.unpairCalls)

        repository.pair("123456")
        assertTrue(repository.disconnectFromServer())
        assertEquals(1, api.unpairCalls)

        api.failure = BackendException(BackendException.Kind.NETWORK, "offline")
        assertFalse(repository.disconnectFromServer())
    }

    @Test
    fun `서버에서 연결이 끊기면 연결과 기기 계정을 잊고 연결 화면으로 돌아간다`() = runTest {
        repository.pair("123456")

        repository.forget()

        assertNull(repository.pairing.value)
        assertNull(settings.pairing)
        assertNull(settings.deviceSession)
        assertFalse(repository.skipped.value)
    }

    @Test
    fun `삭제된 기업에서 못 올린 운행은 새 기업에 연결해도 그 기업으로 올리지 않는다`() = runTest {
        trips.startTrip("old", "device-1234", at = 1_000, odometerKm = 10.0)
        trips.markParked("old", at = 2_000, odometerKm = 15.0)
        trips.completeTrip("old", at = 2_000)
        trips.assignOrg("org-deleted", "driver-old")
        trips.startTrip("new", "device-1234", at = 3_000, odometerKm = 15.0)
        trips.markParked("new", at = 4_000, odometerKm = 20.0)
        trips.completeTrip("new", at = 4_000)
        api.pairResponse = api.pairResponse.copy(orgId = "org-2", driverId = "driver-2")

        repository.pair("123456")

        assertEquals("org-deleted", trips.trip("old").orgId)
        assertEquals(SyncState.SYNCED, trips.trip("old").syncState)
        assertEquals("org-2", trips.trip("new").orgId)
        assertEquals(SyncState.PENDING, trips.trip("new").syncState)
    }

    @Test
    fun `코드가 맞으면 조직에 연결되고 연결 전에 쌓인 운행에도 조직이 채워진다`() = runTest {
        trips.startTrip("t1", "device-1234", at = 1_000, odometerKm = 10.0)

        val result = repository.pair("123456")

        val expected = Pairing(orgId = "org-1", orgName = "데모 조직", driverId = "driver-1", driverName = "기본 운전자")
        assertEquals(PairingResult.Connected(expected), result)
        assertEquals(expected, settings.pairing)
        assertEquals(expected, repository.pairing.value)
        assertFalse(repository.skipped.value)
        assertEquals("org-1", trips.trip("t1").orgId)
        assertEquals("123456", api.lastPairRequest?.code)
        assertEquals("device-1234", api.lastPairRequest?.vehicleId)
        assertEquals("device-user", settings.deviceSession?.userId)
    }

    @Test
    fun `틀리거나 만료된 코드는 연결하지 않는다`() = runTest {
        api.pairResponse = PairResponse(status = "INVALID_CODE")
        assertEquals(PairingResult.InvalidCode, repository.pair("000000"))

        api.pairResponse = PairResponse(status = "EXPIRED_CODE")
        assertEquals(PairingResult.ExpiredCode, repository.pair("111111"))

        assertNull(settings.pairing)
        assertNull(repository.pairing.value)
    }

    @Test
    fun `인터넷이 없으면 오프라인으로 알린다`() = runTest {
        api.failure = BackendException(BackendException.Kind.NETWORK, "offline")
        assertEquals(PairingResult.Offline, repository.pair("123456"))
    }

    @Test
    fun `네트워크 오류가 아닌 예외도 앱을 멈추지 않고 실패로 알린다`() = runTest {
        // 인터넷 권한이 빠졌을 때 실제로 난 오류 (2026-10-26 release 시험)
        api.failure = SecurityException("Permission denied (missing INTERNET permission?)")
        assertTrue(repository.pair("123456") is PairingResult.Failed)
        assertNull(settings.pairing)
    }
}
