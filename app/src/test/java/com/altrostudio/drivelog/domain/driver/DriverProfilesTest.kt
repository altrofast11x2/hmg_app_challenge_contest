package com.altrostudio.drivelog.domain.driver

import com.altrostudio.drivelog.data.trip.ChainVerification
import com.altrostudio.drivelog.data.trip.ChangeField
import com.altrostudio.drivelog.data.vehicle.Comfort
import com.altrostudio.drivelog.domain.driver.FakeDriverDao.Companion.driver
import com.altrostudio.drivelog.fake.FakeCabinControl
import com.altrostudio.drivelog.fake.FakeTripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverProfilesTest {

    private class MemorySettings : DriverSettings {
        override var currentDriverId: String? = null
        override var driverConfirmedAt: Long = 0
        override var lastTripEndedAt: Long = 0
        override var autoComfort: Boolean = true
    }

    private val minjun = driver("d1", "김민준", "영업1팀", tempC = 21.5f, seatHeat = 0, ambient = 0x2F6BE0, order = 0, assigned = true)
    private val seoyeon = driver("d2", "이서연", "기술지원팀", tempC = 24f, seatHeat = 2, ambient = 0xF2B24C, order = 1)

    private fun TestScope.profiles(
        dao: FakeDriverDao = FakeDriverDao(listOf(minjun, seoyeon)),
        cabin: FakeCabinControl = FakeCabinControl(),
        trips: FakeTripRepository = FakeTripRepository(now = { testScheduler.currentTime }),
        settings: MemorySettings = MemorySettings(),
        paired: Boolean = true
    ) = DriverProfiles(dao, cabin, trips, settings, MutableStateFlow(paired), backgroundScope, now = { testScheduler.currentTime })
        .also { runCurrent() }

    @Test
    fun `운전자를 고르면 그분의 온도·열선·무드등으로 차를 맞추고 진행 중인 운행의 사용자 칸에 적는다`() = runTest {
        val cabin = FakeCabinControl(tempC = 22f, seatHeat = 0)
        val trips = FakeTripRepository(now = { testScheduler.currentTime })
        trips.startTrip("t1", "VIN", 0, 100.0)
        val drivers = profiles(cabin = cabin, trips = trips)

        val selection = drivers.select("d2", DriverSource.TOUCH)!!
        runCurrent()

        assertEquals(Comfort(24f, 2, 0xF2B24C), selection.applied)
        assertEquals(24f, cabin.tempC)
        assertEquals(2, cabin.seatHeat)
        assertEquals(0xF2B24C, cabin.ambient.color)
        assertEquals("d2", drivers.current.value?.id)
        val trip = trips.trip("t1")
        assertEquals("이서연", trip.driverName)
        assertEquals("기술지원팀", trip.driverDepartment)
    }

    @Test
    fun `맞춤 설정 자동 적용을 끄면 운전자만 바꾸고 차는 그대로 둔다`() = runTest {
        val cabin = FakeCabinControl(tempC = 22f)
        val settings = MemorySettings().apply { autoComfort = false }
        val drivers = profiles(cabin = cabin, settings = settings)

        val selection = drivers.select("d2", DriverSource.TOUCH)!!

        assertTrue(selection.applied.isEmpty)
        assertEquals(22f, cabin.tempC)
    }

    @Test
    fun `마친 운행의 운전자를 바꾸면 수정 이력이 남고 체인은 그대로 검증된다`() = runTest {
        val trips = FakeTripRepository(now = { testScheduler.currentTime })
        trips.startTrip("t1", "VIN", 0, 100.0)
        trips.setDriver("t1", minjun.ref, "driver:d1", 0)
        trips.markParked("t1", 1_000, 105.0)
        trips.completeTrip("t1", 1_000)

        trips.setDriver("t1", seoyeon.ref, "driver:d2", 2_000)

        val driverChanges = trips.changes.filter { it.field == ChangeField.DRIVER }
        assertEquals(listOf(null, "김민준 · 영업1팀"), driverChanges.map { it.oldValue })
        assertEquals("이서연 · 기술지원팀", driverChanges.last().newValue)
        assertEquals(ChainVerification.Valid(3), trips.verifyChain("VIN"))
    }

    @Test
    fun `말한 이름으로 운전자를 찾는다 (공백·님·성 생략)`() = runTest {
        val drivers = profiles()

        assertEquals("d1", drivers.findByName("김민준")?.id)
        assertEquals("d1", drivers.findByName("김 민준 님")?.id)
        assertEquals("d2", drivers.findByName("서연")?.id)
        assertNull(drivers.findByName("박지훈"))
        assertNull(drivers.findByName(""))
    }

    @Test
    fun `운행이 끝날 때 차의 설정을 기억하고, 읽지 못한 항목은 전에 기억한 값을 둔다`() = runTest {
        val dao = FakeDriverDao(listOf(minjun, seoyeon))
        val drivers = profiles(dao = dao)
        drivers.select("d1", DriverSource.TOUCH)
        runCurrent()

        val changed = drivers.learnComfort(Comfort(tempC = 23f, seatHeat = null, ambientColor = 0x5CC6E8))
        runCurrent()

        assertTrue(changed)
        val row = dao.rows.value.getValue("d1")
        assertEquals(23f, row.comfortTempC)
        assertEquals(0, row.comfortSeatHeat)
        assertEquals(0x5CC6E8, row.comfortAmbientColor)
        assertTrue("서버에 올릴 것으로 표시", row.comfortDirty)
        assertFalse("같은 값이면 다시 올리지 않는다", drivers.learnComfort(Comfort(23f, 0, 0x5CC6E8)))
    }

    @Test
    fun `고른 적이 없거나 2시간 넘게 쉬었다가 타면 운전자를 다시 확인한다`() = runTest {
        val settings = MemorySettings()
        val drivers = profiles(settings = settings)
        assertTrue(drivers.needsConfirmation())

        drivers.select("d1", DriverSource.TOUCH)
        runCurrent()
        assertFalse(drivers.needsConfirmation())

        settings.lastTripEndedAt = testScheduler.currentTime
        assertFalse(drivers.needsConfirmation(testScheduler.currentTime + DriverProfiles.SESSION_GAP_MS))
        assertTrue(drivers.needsConfirmation(testScheduler.currentTime + DriverProfiles.SESSION_GAP_MS + 1))
    }

    @Test
    fun `조직에 연결하지 않은 차는 운전자를 묻지 않는다`() = runTest {
        val drivers = profiles(paired = false)

        assertFalse(drivers.enabled.value)
        assertNull(drivers.currentRef())
        assertFalse(drivers.needsConfirmation())
    }
}
