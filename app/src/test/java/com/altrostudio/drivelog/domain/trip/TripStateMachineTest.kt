package com.altrostudio.drivelog.domain.trip

import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.vehicle.GearPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class TripStateMachineTest {

    private val timeoutMs = 300_000L
    private val machine = TripStateMachine(parkedTimeoutMs = { timeoutMs }, newTripId = { "trip-1" })

    private fun gear(gear: GearPosition, at: Long = 0) = TripEvent.GearChanged(gear, at)

    @Test
    fun `대기 중 기어가 D·R·N으로 바뀌면 운행을 시작한다`() {
        for (moving in listOf(GearPosition.DRIVE, GearPosition.REVERSE, GearPosition.NEUTRAL)) {
            assertEquals(
                Transition(TripState.Driving("trip-1"), TripEffect.StartTrip("trip-1", 10)),
                machine.reduce(TripState.Idle, gear(moving, at = 10))
            )
        }
    }

    @Test
    fun `대기 중 P나 알 수 없는 기어는 무시한다`() {
        assertEquals(Transition(TripState.Idle), machine.reduce(TripState.Idle, gear(GearPosition.PARK)))
        assertEquals(Transition(TripState.Idle), machine.reduce(TripState.Idle, gear(GearPosition.UNKNOWN)))
    }

    @Test
    fun `주행 중 P로 바뀌면 정차로 바뀌고 종료 값을 남긴다`() {
        assertEquals(
            Transition(TripState.Parked("trip-1", 500), TripEffect.MarkParked("trip-1", 500)),
            machine.reduce(TripState.Driving("trip-1"), gear(GearPosition.PARK, at = 500))
        )
    }

    @Test
    fun `주행 중 D와 R 사이 변경이나 분류는 무시한다`() {
        val driving = TripState.Driving("trip-1")
        assertEquals(Transition(driving), machine.reduce(driving, gear(GearPosition.REVERSE)))
        assertEquals(Transition(driving), machine.reduce(driving, TripEvent.Classified(TripPurpose.BUSINESS, 1)))
    }

    @Test
    fun `정차 중 다시 출발하면 같은 운행을 이어 간다`() {
        assertEquals(
            Transition(TripState.Driving("trip-1"), TripEffect.ResumeDriving("trip-1")),
            machine.reduce(TripState.Parked("trip-1", 500), gear(GearPosition.DRIVE, at = 600))
        )
    }

    @Test
    fun `정차 중 분류를 고르면 그 분류로 운행을 마친다`() {
        assertEquals(
            Transition(TripState.Idle, TripEffect.CompleteTrip("trip-1", TripPurpose.COMMUTE, 700)),
            machine.reduce(TripState.Parked("trip-1", 500), TripEvent.Classified(TripPurpose.COMMUTE, 700))
        )
    }

    @Test
    fun `정차가 대기 시간보다 짧으면 이어지고, 다 차면 미분류로 마친다`() {
        val parked = TripState.Parked("trip-1", 1_000)
        assertEquals(Transition(parked), machine.reduce(parked, TripEvent.Tick(1_000 + timeoutMs - 1)))
        assertEquals(
            Transition(TripState.Idle, TripEffect.CompleteTrip("trip-1", null, 1_000 + timeoutMs)),
            machine.reduce(parked, TripEvent.Tick(1_000 + timeoutMs))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `미분류는 운전자가 고를 수 있는 값이 아니다`() {
        TripEvent.Classified(TripPurpose.UNCLASSIFIED, 0)
    }
}
