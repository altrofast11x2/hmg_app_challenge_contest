package com.altrostudio.drivelog.domain.trip

import com.altrostudio.drivelog.data.trip.ChainVerification
import com.altrostudio.drivelog.data.trip.ChangeField
import com.altrostudio.drivelog.data.trip.DriverRef
import com.altrostudio.drivelog.data.trip.HashChain
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.data.vehicle.GearPosition
import com.altrostudio.drivelog.domain.voice.TripAnnouncement
import com.altrostudio.drivelog.domain.voice.VoicePhrases
import com.altrostudio.drivelog.fake.FakeNaviDataSource
import com.altrostudio.drivelog.fake.FakeTripRepository
import com.altrostudio.drivelog.fake.FakeVehicleDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 공용차 운전자, 음성(Gleo AI) 기록, 운행 순간 알림 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderCabinTest {

    private val vehicle = FakeVehicleDataSource()
    private val navi = FakeNaviDataSource()
    private val announcements = mutableListOf<TripAnnouncement>()
    private var driver: DriverRef? = DriverRef("d1", "김민준", "영업1팀")
    private var confirm = false

    private fun TestScope.startRecorder(trips: FakeTripRepository): TripRecorder {
        var nextId = 0
        return TripRecorder(
            vehicle = vehicle,
            navi = navi,
            trips = trips,
            vehicleId = { VEHICLE_ID },
            parkedTimeoutMs = { 300_000L },
            scope = backgroundScope,
            announce = { announcements += it },
            currentDriver = { driver },
            driverNeedsConfirmation = { confirm },
            now = { testScheduler.currentTime },
            newTripId = { "trip-${++nextId}" }
        ).also {
            it.start()
            runCurrent()
        }
    }

    private fun TestScope.repository() = FakeTripRepository(now = { testScheduler.currentTime })

    /** 계기판을 [km]만큼 올린다 (0 km로 끝난 운행은 기어만 바꾼 것으로 보고 버린다) */
    private fun TestScope.drive(km: Float) {
        vehicle.odometer.value = (vehicle.odometer.value ?: 0f) + km
        runCurrent()
    }

    private fun TestScope.shift(gear: GearPosition) {
        vehicle.gearPosition.value = gear
        runCurrent()
    }

    @Test
    fun `운행을 시작하면 지금 운전자를 사용자 칸에 적고 이름으로 안내한다`() = runTest {
        val trips = repository()
        startRecorder(trips)
        confirm = true

        shift(GearPosition.DRIVE)

        val trip = trips.trips.value.single()
        assertEquals("d1", trip.driverId)
        assertEquals("김민준", trip.driverName)
        assertEquals("영업1팀", trip.driverDepartment)
        assertEquals(TripAnnouncement.StartedBy("김민준", confirm = true), announcements.first())
        assertEquals(
            "김민준 님으로 운행 기록을 시작합니다. 다른 분이면 정차한 뒤 화면에서 바꿔 주세요.",
            VoicePhrases.of(announcements.first())
        )
    }

    @Test
    fun `운행을 마치면 운행 기록 뒤에 운전자 이력이 체인에 남는다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        val recorder = startRecorder(trips)
        shift(GearPosition.DRIVE)
        vehicle.odometer.value = 104f
        runCurrent()
        shift(GearPosition.PARK)

        recorder.classify(TripPurpose.BUSINESS)
        runCurrent()

        val fields = trips.changes.sortedBy { it.chainSeq }.map { it.field }
        assertEquals(listOf(ChangeField.DRIVER, ChangeField.PURPOSE), fields)
        assertEquals("김민준 · 영업1팀", trips.changes.first().newValue)
        assertEquals("driver:d1", trips.changes.first().actor)
        assertEquals(ChainVerification.Valid(3), trips.verifyChain(VEHICLE_ID))
    }

    @Test
    fun `주행 중 음성으로 용도·업무 내용을 말하면 정차하자마자 그 용도로 마친다 (분류 화면 없음)`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        val recorder = startRecorder(trips)
        shift(GearPosition.DRIVE)

        val result = async { recorder.recordByVoice(TripPurpose.BUSINESS, "  삼성전자 납품 미팅 ", "voice:d1") }
        runCurrent()
        assertEquals(VoiceRecord.PlannedWhileDriving, result.await())
        assertEquals(TripStatus.DRIVING, trips.trips.value.single().status)

        vehicle.odometer.value = 112f
        runCurrent()
        shift(GearPosition.PARK)

        val trip = trips.trips.value.single()
        assertEquals(TripStatus.COMPLETED, trip.status)
        assertEquals(TripPurpose.BUSINESS, trip.purpose)
        assertEquals("삼성전자 납품 미팅", trip.workNote)
        assertEquals(TripState.Idle, recorder.state.value)
        val purposeChange = trips.changes.single { it.field == ChangeField.PURPOSE }
        assertEquals("voice:d1", purposeChange.actor)
        val noteChange = trips.changes.single { it.field == ChangeField.WORK_NOTE }
        assertEquals("삼성전자 납품 미팅", noteChange.newValue)
        assertTrue("분류를 묻는 정차 안내를 하지 않는다", announcements.none { it is TripAnnouncement.Parked })
        assertEquals(TripAnnouncement.Classified(TripPurpose.BUSINESS, "삼성전자 납품 미팅"), announcements.last())
        assertEquals(ChainVerification.Valid(4), trips.verifyChain(VEHICLE_ID))
    }

    @Test
    fun `정차 중 음성으로 기록하면 바로 마치고, 운행이 없으면 30분 안에 마친 운행을 고친다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        val recorder = startRecorder(trips)
        shift(GearPosition.DRIVE)
        drive(3f)
        shift(GearPosition.PARK)

        val parked = async { recorder.recordByVoice(TripPurpose.COMMUTE, null, "voice:d1") }
        runCurrent()
        assertTrue(parked.await() is VoiceRecord.Completed)
        runCurrent()
        assertEquals(TripPurpose.COMMUTE, trips.trips.value.single().purpose)

        advanceTimeBy(10 * 60_000L)
        val corrected = async { recorder.recordByVoice(TripPurpose.BUSINESS, "거래처 방문", "voice:d1") }
        runCurrent()
        assertTrue(corrected.await() is VoiceRecord.Corrected)
        val trip = trips.trips.value.single()
        assertEquals(TripPurpose.BUSINESS, trip.purpose)
        assertEquals("거래처 방문", trip.workNote)

        advanceTimeBy(TripRecorder.RECENT_TRIP_MS)
        val tooLate = async { recorder.recordByVoice(TripPurpose.PERSONAL, null, "voice:d1") }
        runCurrent()
        assertEquals(VoiceRecord.NoTrip, tooLate.await())
        assertEquals(ChainVerification.Valid(trips.changes.size + 1), trips.verifyChain(VEHICLE_ID))
    }

    @Test
    fun `비업무로 고치면 업무 내용을 지우고 이력의 원문도 해시값으로 가린다`() = runTest {
        val trips = repository()
        val recorder = startRecorder(trips)
        shift(GearPosition.DRIVE)
        async { recorder.recordByVoice(TripPurpose.BUSINESS, "거래처 미팅", "voice:d1") }
        runCurrent()
        drive(2f)
        shift(GearPosition.PARK)
        val tripId = trips.trips.value.single().id

        trips.classify(tripId, TripPurpose.PERSONAL, TripRecorder.DRIVER_ACTOR, testScheduler.currentTime)

        assertNull(trips.trip(tripId).workNote)
        val note = trips.changes.single { it.field == ChangeField.WORK_NOTE }
        assertEquals(HashChain.commitment("거래처 미팅"), note.newValue)
        assertEquals(ChainVerification.Valid(trips.changes.size + 1), trips.verifyChain(VEHICLE_ID))
    }

    @Test
    fun `운행 순간을 차례로 알린다`() = runTest {
        val trips = repository()
        val recorder = startRecorder(trips)
        val moments = mutableListOf<TripMoment>()
        backgroundScope.launch { recorder.moments.toList(moments) }
        runCurrent()

        shift(GearPosition.DRIVE)
        drive(1f)
        shift(GearPosition.PARK)
        shift(GearPosition.DRIVE)
        drive(1f)
        shift(GearPosition.PARK)
        recorder.classify(TripPurpose.COMMUTE)
        runCurrent()

        assertEquals(
            listOf(
                TripMoment.Started("trip-1"),
                TripMoment.Parked("trip-1", awaitingClassification = true),
                TripMoment.Resumed("trip-1"),
                TripMoment.Parked("trip-1", awaitingClassification = true),
                TripMoment.Completed("trip-1", TripPurpose.COMMUTE)
            ),
            moments
        )
    }

    @Test
    fun `운전자가 없으면 지금처럼 기본 안내로 시작한다`() = runTest {
        driver = null
        val trips = repository()
        startRecorder(trips)

        shift(GearPosition.DRIVE)

        assertNull(trips.trips.value.single().driverName)
        assertEquals(TripAnnouncement.Started, announcements.first())
    }

    private companion object {
        const val VEHICLE_ID = "VIN-TEST"
    }
}
