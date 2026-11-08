package com.altrostudio.drivelog.domain.cabin

import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.vehicle.AmbientLight
import com.altrostudio.drivelog.data.vehicle.CabinWindow
import com.altrostudio.drivelog.data.vehicle.GearPosition
import com.altrostudio.drivelog.domain.driver.DriverProfiles
import com.altrostudio.drivelog.domain.driver.DriverSettings
import com.altrostudio.drivelog.domain.driver.DriverSource
import com.altrostudio.drivelog.domain.driver.FakeDriverDao
import com.altrostudio.drivelog.domain.trip.TripMoment
import com.altrostudio.drivelog.fake.FakeCabinControl
import com.altrostudio.drivelog.fake.FakeTripRepository
import com.altrostudio.drivelog.fake.FakeVehicleDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CabinAssistantTest {

    private class MemoryCabinSettings : CabinSettings {
        override var ambientCue: Boolean = true
        override var exitCheck: Boolean = true
        override var savedAmbient: AmbientLight? = null
    }

    private class MemoryDriverSettings : DriverSettings {
        override var currentDriverId: String? = null
        override var driverConfirmedAt: Long = 0
        override var lastTripEndedAt: Long = 0
        override var autoComfort: Boolean = true
    }

    private val palette = AmbientPalette(pending = 0xF2B24C, commute = 0x5CC6E8, business = 0x2F6BE0, personal = 0x3A4555)
    private val original = AmbientLight(color = 0xFFFFFF, brightness = 40, on = false)

    private class Harness(
        val assistant: CabinAssistant,
        val cabin: FakeCabinControl,
        val vehicle: FakeVehicleDataSource,
        val settings: MemoryCabinSettings,
        val moments: MutableSharedFlow<TripMoment>,
        val spoken: MutableList<String>,
        val reports: MutableList<VehicleStatusReport>,
        val drivers: DriverProfiles,
        val dao: FakeDriverDao
    )

    private fun TestScope.harness(cabin: FakeCabinControl = FakeCabinControl(ambient = original)): Harness {
        val vehicle = FakeVehicleDataSource()
        val settings = MemoryCabinSettings()
        val moments = MutableSharedFlow<TripMoment>(extraBufferCapacity = 8)
        val spoken = mutableListOf<String>()
        val reports = mutableListOf<VehicleStatusReport>()
        val dao = FakeDriverDao(listOf(FakeDriverDao.driver("d1", "김민준", "영업1팀", tempC = 21.5f, seatHeat = 0, ambient = 0x2F6BE0)))
        val drivers = DriverProfiles(
            dao, cabin, FakeTripRepository(now = { testScheduler.currentTime }), MemoryDriverSettings(), MutableStateFlow(true), backgroundScope,
            now = { testScheduler.currentTime }
        )
        val assistant = CabinAssistant(
            cabin = cabin,
            vehicle = vehicle,
            drivers = drivers,
            settings = settings,
            palette = palette,
            say = { spoken += it },
            report = { reports += it },
            scope = backgroundScope,
            now = { testScheduler.currentTime }
        )
        assistant.start(moments) { false }
        runCurrent()
        return Harness(assistant, cabin, vehicle, settings, moments, spoken, reports, drivers, dao)
    }

    private fun TestScope.emit(h: Harness, moment: TripMoment) {
        h.moments.tryEmit(moment)
        runCurrent()
    }

    @Test
    fun `정차 후 분류를 기다리면 무드등을 경고색으로, 분류하면 용도 색을 잠깐 보인 뒤 원래대로 되돌린다`() = runTest {
        val h = harness()

        emit(h, TripMoment.Parked("t1", awaitingClassification = true))
        assertEquals(palette.pending, h.cabin.ambient.color)
        assertEquals(true, h.cabin.ambient.on)
        assertEquals(original, h.settings.savedAmbient)

        emit(h, TripMoment.Completed("t1", TripPurpose.BUSINESS))
        assertEquals(palette.business, h.cabin.ambient.color)
        advanceTimeBy(CabinAssistant.FLASH_MS + 1)
        runCurrent()

        assertEquals(original, h.cabin.ambient)
        assertNull(h.settings.savedAmbient)
        assertEquals(listOf(palette.pending, palette.business, original.color), h.cabin.ambientHistory)
    }

    @Test
    fun `정차했다가 다시 출발하면 무드등을 바로 되돌린다`() = runTest {
        val h = harness()
        emit(h, TripMoment.Parked("t1", awaitingClassification = true))

        emit(h, TripMoment.Resumed("t1"))

        assertEquals(original, h.cabin.ambient)
        assertNull(h.settings.savedAmbient)
    }

    @Test
    fun `무드등 신호를 끄면 무드등을 건드리지 않는다`() = runTest {
        val h = harness()
        h.settings.ambientCue = false

        emit(h, TripMoment.Parked("t1", awaitingClassification = true))
        emit(h, TripMoment.Completed("t1", TripPurpose.COMMUTE))
        advanceTimeBy(CabinAssistant.FLASH_MS + 1)
        runCurrent()

        assertTrue(h.cabin.ambientHistory.isEmpty())
    }

    @Test
    fun `앱이 신호 도중 꺼졌다가 다시 켜지면 저장해 둔 무드등으로 되돌린다`() = runTest {
        val cabin = FakeCabinControl(ambient = AmbientLight(palette.pending, 60, true))
        val settings = MemoryCabinSettings().apply { savedAmbient = original }
        val drivers = DriverProfiles(
            FakeDriverDao(), cabin, FakeTripRepository(), MemoryDriverSettings(), MutableStateFlow(false), backgroundScope
        )
        CabinAssistant(cabin, FakeVehicleDataSource(), drivers, settings, palette, {}, {}, scope = backgroundScope)
            .start(MutableSharedFlow()) { false }
        runCurrent()

        assertEquals(original, cabin.ambient)
        assertNull(settings.savedAmbient)
    }

    @Test
    fun `운행을 마치면 열린 창문·켜진 실내등·배터리를 확인해 알리고, P에서 누르면 한 번에 정리한다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.FRONT_LEFT), cabinLightOn = true)
        val h = harness(cabin)
        h.vehicle.socPercent = 15

        emit(h, TripMoment.Completed("t1", purpose = null))
        val check = h.assistant.exitCheck.value!!
        assertEquals(setOf(CabinWindow.FRONT_LEFT), check.openWindows)
        assertTrue(check.cabinLightOn)
        assertTrue(check.lowBattery)
        assertEquals(
            "내리기 전에 확인해 주세요. 운전석 창문이 열려 있어요. 실내등이 켜져 있어요. 배터리가 15퍼센트예요. 다음 분을 위해 충전해 주세요. 화면에서 한 번에 정리할 수 있어요.",
            h.spoken.single()
        )
        assertEquals(15, h.reports.last().socPercent)

        h.vehicle.gearPosition.value = GearPosition.PARK
        val tidied = h.assistant.tidyUp(setOf(TidyItem.WINDOWS, TidyItem.LIGHTS))!!

        assertTrue(tidied.openWindows.isEmpty())
        assertFalse(tidied.cabinLightOn)
        assertEquals(setOf(TidyItem.WINDOWS, TidyItem.LIGHTS), tidied.tidied)
        assertEquals("창문을 닫고 실내등을 껐습니다.", h.spoken.last())
        assertEquals(setOf(TidyItem.WINDOWS, TidyItem.LIGHTS), h.reports.last().tidied)
    }

    @Test
    fun `정차 화면에서 나중에를 누르면 분류를 기다리지 않고 하차 점검을 한다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.REAR_LEFT))
        val h = harness(cabin)

        emit(h, TripMoment.Parked("t1", awaitingClassification = true))
        assertNull(h.assistant.exitCheck.value)

        h.assistant.checkBeforeLeaving("t1")
        runCurrent()

        val check = h.assistant.exitCheck.value!!
        assertEquals("t1", check.tripId)
        assertEquals(setOf(CabinWindow.REAR_LEFT), check.openWindows)
        assertTrue(h.spoken.last().startsWith("내리기 전에 확인해 주세요."))
        assertEquals("t1", h.reports.last().tripId)
    }

    @Test
    fun `하차 점검을 끄면 나중에를 눌러도 점검하지 않는다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.REAR_LEFT))
        val h = harness(cabin)
        h.settings.exitCheck = false

        h.assistant.checkBeforeLeaving("t1")
        runCurrent()

        assertNull(h.assistant.exitCheck.value)
        assertTrue(h.reports.isEmpty())
    }

    @Test
    fun `주행 중(D)이거나 주행 제한 중이면 창문·실내등·문을 바꾸지 않는다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.REAR_LEFT))
        val h = harness(cabin)
        emit(h, TripMoment.Completed("t1", purpose = null))

        h.vehicle.gearPosition.value = GearPosition.DRIVE
        h.assistant.tidyUp(setOf(TidyItem.WINDOWS))
        assertEquals(setOf(CabinWindow.REAR_LEFT), cabin.openWindows)

        h.vehicle.gearPosition.value = GearPosition.PARK
        h.vehicle.restricted.value = true
        h.assistant.tidyUp(setOf(TidyItem.WINDOWS))
        assertEquals(setOf(CabinWindow.REAR_LEFT), cabin.openWindows)
    }

    @Test
    fun `차가 정리 요청을 받아들이지 않으면 실패한 항목으로 남긴다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.FRONT_RIGHT))
        val h = harness(cabin)
        emit(h, TripMoment.Completed("t1", purpose = null))
        cabin.accepts = false

        val result = h.assistant.tidyUp(setOf(TidyItem.WINDOWS))!!

        assertEquals(setOf(TidyItem.WINDOWS), result.failed)
        assertTrue(result.tidied.isEmpty())
    }

    @Test
    fun `점검할 것이 없으면 카드를 띄우지 않고, 다음 운행을 시작하면 카드를 지운다`() = runTest {
        val h = harness()
        emit(h, TripMoment.Completed("t1", purpose = null))
        assertNull(h.assistant.exitCheck.value)
        assertTrue(h.spoken.isEmpty())
        assertNotNull("차 상태는 보고한다", h.reports.lastOrNull())

        h.cabin.cabinLightOn = true
        emit(h, TripMoment.Completed("t2", purpose = null))
        assertNotNull(h.assistant.exitCheck.value)
        emit(h, TripMoment.Started("t3"))
        assertNull(h.assistant.exitCheck.value)
    }

    @Test
    fun `정차할 때 지금 운전자의 설정을 기억한다 - 무드등은 신호 전 색으로`() = runTest {
        val cabin = FakeCabinControl(tempC = 23.5f, seatHeat = 3, ambient = AmbientLight(0x112233, 50, true))
        val h = harness(cabin)
        h.drivers.select("d1", DriverSource.TOUCH, applyComfort = false)
        runCurrent()

        emit(h, TripMoment.Parked("t1", awaitingClassification = true))

        val row = h.dao.rows.value.getValue("d1")
        assertEquals(23.5f, row.comfortTempC)
        assertEquals(3, row.comfortSeatHeat)
        assertEquals(0x112233, row.comfortAmbientColor)
    }

    @Test
    fun `차 상태 보기는 배터리·계기판·창문·문·실내등·맞춤 설정을 한 번에 읽는다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.REAR_RIGHT), doorsLocked = false, cabinLightOn = true)
        val h = harness(cabin)
        h.vehicle.socPercent = 77
        h.vehicle.odometer.value = 426f

        val status = h.assistant.readStatus()

        assertEquals(77, status.socPercent)
        assertEquals(426f, status.odometerKm)
        assertEquals(setOf(CabinWindow.REAR_RIGHT), status.openWindows)
        assertEquals(false, status.doorsLocked)
        assertEquals(true, status.cabinLightOn)
        assertEquals(22f, status.comfort.tempC)
        assertTrue(status.canControl)
        assertEquals(setOf(TidyItem.WINDOWS, TidyItem.DOORS, TidyItem.LIGHTS), status.actions)
        assertTrue(h.spoken.isEmpty())
    }

    @Test
    fun `차 상태 보기에서 누르면 P에서만 바꾸고 다시 읽어 보여 준다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.FRONT_LEFT), doorsLocked = false)
        val h = harness(cabin)

        h.vehicle.gearPosition.value = GearPosition.DRIVE
        val blocked = h.assistant.control(setOf(TidyItem.WINDOWS))
        assertFalse(blocked.canControl)
        assertEquals(setOf(CabinWindow.FRONT_LEFT), cabin.openWindows)

        h.vehicle.gearPosition.value = GearPosition.PARK
        val done = h.assistant.control(setOf(TidyItem.WINDOWS, TidyItem.DOORS))
        assertEquals(emptySet<CabinWindow>(), done.openWindows)
        assertEquals(true, done.doorsLocked)
        assertTrue(done.actions.isEmpty())
        assertEquals("창문을 닫고 문을 잠갔습니다.", h.spoken.last())
    }

    @Test
    fun `차 상태 보기에서 정리하면 떠 있는 하차 점검 카드도 고친다`() = runTest {
        val cabin = FakeCabinControl(ambient = original, openWindows = mutableSetOf(CabinWindow.FRONT_LEFT))
        val h = harness(cabin)
        emit(h, TripMoment.Completed("t1", purpose = null))

        h.assistant.control(setOf(TidyItem.WINDOWS))

        val check = h.assistant.exitCheck.value!!
        assertTrue(check.openWindows.isEmpty())
        assertEquals(setOf(TidyItem.WINDOWS), check.tidied)
    }

    @Test
    fun `차 상태를 소리로 읽는 문장`() {
        val base = CarStatus(
            readAt = 0, socPercent = 77, odometerKm = 426.4f, openWindows = emptySet(), doorsLocked = true, cabinLightOn = false,
            comfort = com.altrostudio.drivelog.data.vehicle.Comfort(), canControl = true
        )
        assertEquals("배터리 77퍼센트, 계기판 426킬로미터입니다. 창문은 모두 닫혀 있고, 문은 잠겨 있고, 실내등은 꺼져 있어요.", CabinPhrases.status(base))
        assertEquals(
            "배터리 77퍼센트, 계기판 426킬로미터입니다. 운전석 창문이 열려 있고, 문은 잠겨 있고, 실내등은 꺼져 있어요. 차 상태 화면에서 바로 정리할 수 있어요.",
            CabinPhrases.status(base.copy(openWindows = setOf(CabinWindow.FRONT_LEFT)))
        )
        assertEquals("배터리 77퍼센트입니다. 문은 잠겨 있어요.", CabinPhrases.status(base.copy(odometerKm = null, openWindows = null, cabinLightOn = null)))
        assertEquals("차 상태를 읽지 못했어요.", CabinPhrases.status(base.copy(socPercent = null, odometerKm = null, openWindows = null, doorsLocked = null, cabinLightOn = null)))
    }

    @Test
    fun `정리 결과 문장과 창문 주어`() {
        assertEquals("창문을 닫았습니다.", CabinPhrases.tidied(setOf(TidyItem.WINDOWS)))
        assertEquals("실내등을 껐습니다.", CabinPhrases.tidied(setOf(TidyItem.LIGHTS)))
        assertEquals("창문을 닫고 실내등을 끄고 문을 잠갔습니다.", CabinPhrases.tidied(TidyItem.entries.toSet()))
        assertEquals("창문 2개가", CabinPhrases.windowsSubject(setOf(CabinWindow.FRONT_LEFT, CabinWindow.REAR_RIGHT)))
        assertEquals("조수석 창문이", CabinPhrases.windowsSubject(setOf(CabinWindow.FRONT_RIGHT)))
    }

    @Test
    fun `운전자 맞춤 적용 문장`() {
        assertEquals(
            "김민준 님 설정으로 맞췄어요. 온도 21.5도, 시트 열선 2단계, 무드등.",
            CabinPhrases.applied("김민준", com.altrostudio.drivelog.data.vehicle.Comfort(21.5f, 2, 0x2F6BE0))
        )
        assertEquals("이서연 님 설정으로 맞췄어요. 온도 24도, 시트 통풍 1단계.", CabinPhrases.applied("이서연", com.altrostudio.drivelog.data.vehicle.Comfort(24f, -1, null)))
        assertEquals("박지훈 님으로 기록할게요.", CabinPhrases.applied("박지훈", com.altrostudio.drivelog.data.vehicle.Comfort()))
    }
}
