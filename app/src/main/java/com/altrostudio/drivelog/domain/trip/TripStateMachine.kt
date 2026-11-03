package com.altrostudio.drivelog.domain.trip

import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.vehicle.GearPosition
import java.util.UUID

/** 운행 감지 상태: 대기 → 주행 중 → 정차 → (종료하면) 대기 */
sealed interface TripState {
    data object Idle : TripState
    data class Driving(val tripId: String) : TripState
    data class Parked(val tripId: String, val parkedAt: Long) : TripState
}

sealed interface TripEvent {
    data class GearChanged(val gear: GearPosition, val at: Long) : TripEvent

    /** 정차 중 운전자가 용도를 골랐다. [actor]는 수정 이력의 "바꾼 사람" (화면 누름, 음성) */
    data class Classified(val purpose: TripPurpose, val at: Long, val actor: String = TripRecorder.DRIVER_ACTOR) : TripEvent {
        init {
            require(purpose != TripPurpose.UNCLASSIFIED) { "미분류는 고를 수 없습니다" }
        }
    }

    /** 정차가 충분히 길어졌는지 확인한다 */
    data class Tick(val at: Long) : TripEvent
}

/** 상태가 바뀔 때 바로 저장할 일 */
sealed interface TripEffect {
    data class StartTrip(val tripId: String, val at: Long) : TripEffect
    data class MarkParked(val tripId: String, val at: Long) : TripEffect
    data class ResumeDriving(val tripId: String) : TripEffect

    /** [purpose]가 null이면 미분류로 마친다 */
    data class CompleteTrip(
        val tripId: String,
        val purpose: TripPurpose?,
        val at: Long,
        val actor: String = TripRecorder.DRIVER_ACTOR
    ) : TripEffect
}

data class Transition(val state: TripState, val effect: TripEffect? = null)

/**
 * 입력을 받아 다음 상태와 할 일만 정하는 순수 상태 머신. 저장과 차량 값 조회는 [TripRecorder]가 한다.
 * - 기어가 P에서 벗어나면(D/R/N) 운행을 시작한다
 * - P로 들어가면 정차. 정차 중 다시 출발하면 같은 운행을 이어 간다 (잠깐 정차)
 * - 정차 중 운전자가 용도를 고르거나, P가 정해진 시간 동안 이어지면 운행을 마친다
 * - 신호 대기·Auto Hold는 기어가 D 그대로라 정차로 보지 않는다
 */
class TripStateMachine(
    private val parkedTimeoutMs: () -> Long,
    private val newTripId: () -> String = { UUID.randomUUID().toString() }
) {

    fun reduce(state: TripState, event: TripEvent): Transition = when (state) {
        TripState.Idle -> when {
            event is TripEvent.GearChanged && event.gear.isMoving -> {
                val tripId = newTripId()
                Transition(TripState.Driving(tripId), TripEffect.StartTrip(tripId, event.at))
            }
            else -> Transition(state)
        }

        is TripState.Driving -> when {
            event is TripEvent.GearChanged && event.gear == GearPosition.PARK ->
                Transition(TripState.Parked(state.tripId, event.at), TripEffect.MarkParked(state.tripId, event.at))
            else -> Transition(state)
        }

        is TripState.Parked -> when (event) {
            is TripEvent.GearChanged ->
                if (event.gear.isMoving) {
                    Transition(TripState.Driving(state.tripId), TripEffect.ResumeDriving(state.tripId))
                } else {
                    Transition(state)
                }
            is TripEvent.Classified ->
                Transition(TripState.Idle, TripEffect.CompleteTrip(state.tripId, event.purpose, event.at, event.actor))
            is TripEvent.Tick ->
                if (event.at - state.parkedAt >= parkedTimeoutMs()) {
                    Transition(TripState.Idle, TripEffect.CompleteTrip(state.tripId, null, event.at))
                } else {
                    Transition(state)
                }
        }
    }

    private val GearPosition.isMoving: Boolean
        get() = this == GearPosition.DRIVE || this == GearPosition.REVERSE || this == GearPosition.NEUTRAL
}
