package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.PairResponse
import com.altrostudio.drivelog.fake.FakeTripRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PairingRepositoryTest {

    private val api = FakeBackendApi()
    private val settings = MemorySyncSettings(vin = null).apply { pairingSkipped = true }
    private val trips = FakeTripRepository(now = { 0L })
    private val repository = PairingRepository(
        api, DeviceAuth(api, settings, now = { 0L }), settings, vehicleId = { "device-1234" }, trips, isConfigured = true
    )

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
}
