package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.driver.DriverEntity
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.RemoteComfort
import com.altrostudio.drivelog.data.remote.RemoteDriver
import com.altrostudio.drivelog.data.remote.RemoteVehicleStatus
import com.altrostudio.drivelog.domain.driver.FakeDriverDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinSyncTest {

    private class MemoryPrefs : CabinSyncPrefs {
        override var pendingStatus: RemoteVehicleStatus? = null
        override var rosterFetchedAt: Long = 0
    }

    private var now = 1_000_000L
    private val api = FakeBackendApi()
    private val auth = DeviceAuth(api, MemorySyncSettings())
    private val dao = FakeDriverDao()
    private val prefs = MemoryPrefs()
    private val sync = CabinSync(api, RoomCabinSyncStore(dao, prefs), now = { now })

    private val minjun = RemoteDriver("d1", "김민준", "영업1팀", RemoteComfort(21.5f, 0, "#2F6BE0"), assigned = true)
    private val seoyeon = RemoteDriver("d2", "이서연", "기술지원팀", RemoteComfort(24f, 2, "#f2b24c"))

    @Test
    fun `서버 명단을 받아 순서대로 저장하고, 명단에서 빠진 운전자는 지운다`() = runTest {
        dao.upsert(listOf(FakeDriverDao.driver("old", "퇴사자")))
        api.roster = listOf(minjun, seoyeon)

        val result = sync.sync(auth)

        assertEquals(2, result.rosterSize)
        val rows = dao.drivers()
        assertEquals(listOf("d1", "d2"), rows.map { it.id })
        assertEquals(0x2F6BE0, rows[0].comfortAmbientColor)
        assertEquals(0xF2B24C, rows[1].comfortAmbientColor)
        assertTrue(rows[0].assigned)
        assertEquals(now, prefs.rosterFetchedAt)
    }

    @Test
    fun `명단은 10분에 한 번만 다시 받고, 조직에 새로 연결하면 바로 받는다`() = runTest {
        api.roster = listOf(minjun)
        sync.sync(auth)
        api.roster = listOf(minjun, seoyeon)

        now += CabinSync.ROSTER_TTL_MS - 1
        assertNull(sync.sync(auth).rosterSize)

        sync.refreshRosterSoon()
        assertEquals(2, sync.sync(auth).rosterSize)
    }

    @Test
    fun `차에서 바뀐 맞춤 설정을 올리고, 올리는 동안 다시 바뀐 것은 다음에 또 올린다`() = runTest {
        api.roster = listOf(minjun)
        sync.sync(auth)
        dao.updateComfort("d1", 23f, 1, 0x5CC6E8, at = 5)

        val result = sync.sync(auth)

        assertEquals(1, result.comfortsSaved)
        assertEquals(RemoteComfort(23f, 1, "#5CC6E8"), api.savedComforts["d1"])
        assertFalse(dao.rows.value.getValue("d1").comfortDirty)
    }

    @Test
    fun `명단을 다시 받아도 아직 올리지 못한 차의 맞춤 설정은 덮어쓰지 않는다`() = runTest {
        api.roster = listOf(minjun)
        sync.sync(auth)
        dao.updateComfort("d1", 25f, 3, null, at = 7)

        dao.replaceRoster(listOf(DriverEntity("d1", "김민준", "영업1팀", 21.5f, 0, 0x2F6BE0, true, false, 0, false, 9)))

        val row = dao.rows.value.getValue("d1")
        assertEquals(25f, row.comfortTempC)
        assertTrue(row.comfortDirty)
    }

    @Test
    fun `차량 상태를 보고하면 대기열에서 지운다`() = runTest {
        val status = RemoteVehicleStatus(reportedAtMs = 42, tripId = "t1", openWindows = listOf("FRONT_LEFT"), tidied = listOf("WINDOWS"))
        prefs.pendingStatus = status

        val result = sync.sync(auth)

        assertTrue(result.statusSent)
        assertEquals(listOf(status), api.statusReports)
        assertNull(prefs.pendingStatus)
    }

    @Test
    fun `서버에 0010이 아직 없으면(404) 실패를 알리되 대기열은 남겨 다음에 다시 보낸다`() = runTest {
        api.cabinFunctionsMissing = true
        val status = RemoteVehicleStatus(reportedAtMs = 42)
        prefs.pendingStatus = status

        val result = sync.sync(auth)

        assertTrue(result.missingOnServer)
        assertEquals(status, prefs.pendingStatus)
        assertEquals(0L, prefs.rosterFetchedAt)
    }

    @Test
    fun `색 문자열 변환`() {
        assertEquals(0x2F6BE0, parseRgb("#2F6BE0"))
        assertEquals(0x2F6BE0, parseRgb("2f6be0"))
        assertNull(parseRgb("#12345"))
        assertNull(parseRgb("#GGGGGG"))
        assertEquals("#05222D", formatRgb(0xFF05222D.toInt()))
    }
}
