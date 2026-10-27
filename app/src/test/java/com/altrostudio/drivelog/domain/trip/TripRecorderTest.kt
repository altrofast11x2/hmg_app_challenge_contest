package com.altrostudio.drivelog.domain.trip

import com.altrostudio.drivelog.data.navi.NaviArrival
import com.altrostudio.drivelog.data.trip.ChainVerification
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.data.vehicle.GearPosition
import com.altrostudio.drivelog.domain.voice.TripAnnouncement
import com.altrostudio.drivelog.domain.voice.VoicePhrases
import com.altrostudio.drivelog.fake.FakeNaviDataSource
import com.altrostudio.drivelog.fake.FakeTripRepository
import com.altrostudio.drivelog.fake.FakeVehicleDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderTest {

    private val vehicle = FakeVehicleDataSource()
    private val navi = FakeNaviDataSource()
    private val timeoutMs = 300_000L
    private val announcements = mutableListOf<TripAnnouncement>()

    private fun TestScope.startRecorder(trips: FakeTripRepository): TripRecorder {
        var nextId = 0
        return TripRecorder(
            vehicle = vehicle,
            navi = navi,
            trips = trips,
            vehicleId = { VEHICLE_ID },
            parkedTimeoutMs = { timeoutMs },
            scope = backgroundScope,
            announce = { announcements += it },
            now = { testScheduler.currentTime },
            newTripId = { "trip-${++nextId}" }
        ).also {
            it.start()
            runCurrent()
        }
    }

    private fun TestScope.repository() = FakeTripRepository(now = { testScheduler.currentTime })

    private fun TestScope.shift(gear: GearPosition) {
        vehicle.gearPosition.value = gear
        runCurrent()
    }

    @Test
    fun `기어를 D로 바꾸면 계기판 거리·배터리·주소와 함께 운행이 시작된다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        startRecorder(trips)

        shift(GearPosition.DRIVE)

        val trip = trips.trips.value.single()
        assertEquals(TripStatus.DRIVING, trip.status)
        assertEquals(VEHICLE_ID, trip.vehicleId)
        assertEquals(100.0, trip.startOdometerKm!!, 0.001)
        assertEquals(80, trip.startSocPercent)
        assertEquals("서울 강남구 테헤란로 1", trip.startAddress)
    }

    @Test
    fun `P로 정차한 뒤 일반업무를 고르면 운행이 완료되고 해시 체인이 이어진다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        val recorder = startRecorder(trips)

        shift(GearPosition.DRIVE)
        vehicle.odometer.value = 112.3f
        runCurrent()
        vehicle.socPercent = 75
        navi.place = navi.place?.copy(address = "경기 성남시 분당구")
        shift(GearPosition.PARK)

        val parked = trips.trips.value.single()
        assertEquals(TripStatus.PARKED, parked.status)
        assertEquals(112.3, parked.endOdometerKm!!, 0.001)
        assertEquals(75, parked.endSocPercent)
        assertTrue(recorder.state.value is TripState.Parked)

        recorder.classify(TripPurpose.BUSINESS)
        runCurrent()

        val completed = trips.trips.value.single()
        assertEquals(TripStatus.COMPLETED, completed.status)
        assertEquals(TripPurpose.BUSINESS, completed.purpose)
        assertEquals("경기 성남시 분당구", completed.visitNote)
        assertEquals(TripState.Idle, recorder.state.value)
        assertEquals(ChainVerification.Valid(2), trips.verifyChain(VEHICLE_ID))
    }

    @Test
    fun `운행 시작, 정차(거리·시간·지난번 이곳의 용도), 분류 결과를 음성으로 알린다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        val recorder = startRecorder(trips)

        shift(GearPosition.DRIVE)
        vehicle.odometer.value = 112.3f
        advanceTimeBy(25 * 60_000L)
        shift(GearPosition.PARK)
        recorder.classify(TripPurpose.BUSINESS)
        runCurrent()
        // 같은 곳으로 다시 운행
        shift(GearPosition.DRIVE)
        vehicle.odometer.value = 120f
        advanceTimeBy(10 * 60_000L)
        shift(GearPosition.PARK)

        assertEquals(
            listOf(
                "운행 기록을 시작합니다.",
                "운행을 마쳤습니다. 12.3킬로미터, 25분 운행했습니다. 화면에서 용도를 골라 주세요.",
                "일반업무로 기록했습니다.",
                "운행 기록을 시작합니다.",
                "운행을 마쳤습니다. 7.7킬로미터, 10분 운행했습니다. 이곳은 지난번에 일반업무로 기록했습니다. 화면에서 용도를 골라 주세요."
            ),
            announcements.map(VoicePhrases::of)
        )
    }

    @Test
    fun `대기 시간이 지나 미분류로 끝나면 분류 결과는 알리지 않는다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        startRecorder(trips)

        shift(GearPosition.DRIVE)
        vehicle.odometer.value = 105f
        advanceTimeBy(5 * 60_000L)
        shift(GearPosition.PARK)
        advanceTimeBy(timeoutMs + 1)
        runCurrent()

        assertEquals(TripStatus.COMPLETED, trips.trips.value.single().status)
        assertEquals(2, announcements.size)
        assertTrue(announcements.last() is TripAnnouncement.Parked)
    }

    @Test
    fun `잠깐 정차했다가 다시 출발하면 같은 운행을 이어 간다`() = runTest {
        val trips = repository()
        startRecorder(trips)

        shift(GearPosition.DRIVE)
        shift(GearPosition.PARK)
        shift(GearPosition.DRIVE)

        val trip = trips.trips.value.single()
        assertEquals(TripStatus.DRIVING, trip.status)
        assertNull(trip.endedAt)
        assertNull(trip.endOdometerKm)
    }

    @Test
    fun `P가 대기 시간 동안 이어지면 미분류로 운행을 마친다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        startRecorder(trips)

        shift(GearPosition.DRIVE)
        vehicle.odometer.value = 105f
        runCurrent()
        shift(GearPosition.PARK)
        advanceTimeBy(timeoutMs - 1)
        runCurrent()
        assertEquals(TripStatus.PARKED, trips.trips.value.single().status)

        advanceTimeBy(2)
        runCurrent()
        val trip = trips.trips.value.single()
        assertEquals(TripStatus.COMPLETED, trip.status)
        assertEquals(TripPurpose.UNCLASSIFIED, trip.purpose)
        assertEquals(ChainVerification.Valid(1), trips.verifyChain(VEHICLE_ID))
    }

    @Test
    fun `비업무로 분류하면 주소와 경유지를 지우고 거리만 남긴다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 50f
        val recorder = startRecorder(trips)

        shift(GearPosition.DRIVE)
        navi.arrivalEvents.emit(NaviArrival(NaviArrival.Kind.DESTINATION, "마트", "경기 성남시"))
        runCurrent()
        vehicle.odometer.value = 58f
        runCurrent()
        shift(GearPosition.PARK)
        recorder.classify(TripPurpose.PERSONAL)
        runCurrent()

        val trip = trips.trips.value.single()
        assertEquals(TripPurpose.PERSONAL, trip.purpose)
        assertNull(trip.startAddress)
        assertNull(trip.endAddress)
        assertNull(trip.visitNote)
        assertEquals(8.0, trip.endOdometerKm!! - trip.startOdometerKm!!, 0.001)
        assertTrue(trips.stops.isEmpty())
        assertEquals(ChainVerification.Valid(2), trips.verifyChain(VEHICLE_ID))
    }

    @Test
    fun `주행 중 앱이 죽었다 켜지면 마지막으로 주행을 확인한 시각에 정차한 것으로 본다`() = runTest {
        val trips = repository()
        trips.trips.value = listOf(
            TripEntity(
                id = "old",
                vehicleId = VEHICLE_ID,
                status = TripStatus.DRIVING,
                startedAt = 1_000,
                lastSeenAt = 5_000,
                startOdometerKm = 10.0,
                updatedAt = 5_000
            )
        )
        advanceTimeBy(3_600_000)
        vehicle.gearPosition.value = GearPosition.PARK

        startRecorder(trips)

        val trip = trips.trip("old")
        assertEquals(TripStatus.COMPLETED, trip.status)
        assertEquals(5_000L, trip.endedAt)
    }

    @Test
    fun `기어만 잠깐 바꾸고 움직이지 않으면 운행으로 남기지 않는다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        val recorder = startRecorder(trips)

        shift(GearPosition.DRIVE)
        advanceTimeBy(20_000)
        shift(GearPosition.PARK)

        assertTrue(trips.trips.value.isEmpty())
        assertEquals(TripState.Idle, recorder.state.value)
        // 시작은 알렸지만, 운행이 아니므로 정차 후 분류를 묻지 않는다
        assertEquals(listOf<TripAnnouncement>(TripAnnouncement.Started), announcements)
    }

    @Test
    fun `거리가 0이어도 1분 넘게 달렸으면 (계기판이 1 km 단위인 차의 짧은 운행) 남긴다`() = runTest {
        val trips = repository()
        vehicle.odometer.value = 100f
        startRecorder(trips)

        shift(GearPosition.DRIVE)
        advanceTimeBy(90_000)
        shift(GearPosition.PARK)

        assertEquals(TripStatus.PARKED, trips.trips.value.single().status)
    }

    private companion object {
        const val VEHICLE_ID = "VIN-TEST"
    }
}
