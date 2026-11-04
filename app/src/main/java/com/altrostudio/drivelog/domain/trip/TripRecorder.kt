package com.altrostudio.drivelog.domain.trip

import android.util.Log
import com.altrostudio.drivelog.data.navi.NaviArrival
import com.altrostudio.drivelog.data.navi.NaviDataSource
import com.altrostudio.drivelog.data.trip.DriverRef
import com.altrostudio.drivelog.data.trip.TripLedger
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripRepository
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.data.vehicle.GearPosition
import com.altrostudio.drivelog.data.vehicle.VehicleDataSource
import com.altrostudio.drivelog.domain.voice.TripAnnouncement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

/**
 * 운행 자동 감지. 기어 변화를 [TripStateMachine]에 넣고, 나온 할 일을 DB에 바로 저장한다.
 * 시동이 꺼지면 앱이 갑자기 죽을 수 있어 상태가 바뀔 때마다 즉시 저장하고, 다시 시작하면 진행 중인 운행을 이어 간다.
 * 모든 입력은 한 줄로 차례차례 처리한다 (동시에 DB를 고치지 않도록).
 */
class TripRecorder(
    private val vehicle: VehicleDataSource,
    private val navi: NaviDataSource,
    private val trips: TripRepository,
    private val vehicleId: suspend () -> String,
    private val parkedTimeoutMs: () -> Long,
    private val scope: CoroutineScope,
    private val announce: (TripAnnouncement) -> Unit = {},
    /** 지금 운전자 (공용차 운전자 확인). 기록을 시작할 때 운행에 적는다 */
    private val currentDriver: () -> DriverRef? = { null },
    /** 운전자를 오래 확인하지 않았으면 시작 안내에서 바꿀 수 있다고 알린다 */
    private val driverNeedsConfirmation: () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
    newTripId: () -> String = { UUID.randomUUID().toString() }
) {

    private val machine = TripStateMachine(parkedTimeoutMs, newTripId)
    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)
    private val _state = MutableStateFlow<TripState>(TripState.Idle)
    val state: StateFlow<TripState> = _state.asStateFlow()

    private val _moments = MutableSharedFlow<TripMoment>(extraBufferCapacity = MOMENT_BUFFER)

    /** 저장을 마친 운행 순간 (무드등 신호·하차 점검·운전자 설정 기억이 듣는다) */
    val moments: SharedFlow<TripMoment> = _moments.asSharedFlow()

    private var job: Job? = null
    private var lastOdometerKm: Float? = null
    private var parkedTimer: Job? = null

    // 앱이 주행 중에 죽었다가 다시 켜졌다면, 마지막으로 주행을 확인한 시각을 종료 시각으로 쓴다
    private var restoredLastSeenAt: Long? = null

    // 말로 기록에서 기록 전에 이미 되읽고 확인받은 운행: 분류 안내에서 업무 내용을 다시 읽지 않는다
    private var noteConfirmedTripId: String? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        job = scope.launch {
            val id = vehicleId()
            restore()
            launch { vehicle.odometerKm.catch { log("계기판 구독 실패", it) }.collect { inputs.send(Input.Odometer(it)) } }
            launch { vehicle.gear.catch { log("기어 구독 실패", it) }.collect { inputs.send(Input.Event(TripEvent.GearChanged(it, now()))) } }
            launch { navi.arrivals.catch { log("내비 도착 구독 실패", it) }.collect { inputs.send(Input.Arrival(it)) } }
            for (input in inputs) {
                try {
                    handle(input, id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 입력 내용(방문처 이름·주소)은 개인정보라 로그에 남기지 않는다 (Pleos 앱 보안 가이드)
                    log("운행 기록 처리 실패: ${input::class.simpleName}", e)
                }
            }
        }
    }

    /** 동의를 철회하면 기록을 멈춘다. 다시 동의하면 [start]로 처음부터 시작한다 */
    fun stop() {
        job?.cancel()
        job = null
        parkedTimer?.cancel()
        parkedTimer = null
        while (inputs.tryReceive().isSuccess) Unit
        lastOdometerKm = null
        restoredLastSeenAt = null
        _state.value = TripState.Idle
        started.set(false)
    }

    /** 정차 중 운전자가 고른 용도로 운행을 마친다. 주행 중에는 무시된다 */
    fun classify(purpose: TripPurpose, actor: String = DRIVER_ACTOR) {
        inputs.trySend(Input.Event(TripEvent.Classified(purpose, now(), actor)))
    }

    /**
     * 음성(Gleo AI 명령 또는 앱이 직접 듣는 [com.altrostudio.drivelog.domain.voice.VoiceJournal])으로
     * 용도·업무 내용을 기록한다. 화면을 누르지 않아도 된다.
     * - 주행 중: 이번 운행에 미리 적어 두고, 정차하면 그 용도로 바로 마친다
     * - 정차 중: 지금 운행을 그 용도로 마친다
     * - 운행이 없으면: 최근 [RECENT_TRIP_MS] 안에 마친 운행을 그 용도로 고친다
     */
    suspend fun recordByVoice(purpose: TripPurpose, workNote: String?, actor: String, readBackNote: Boolean = true): VoiceRecord {
        val result = CompletableDeferred<VoiceRecord>()
        inputs.send(Input.Voice(purpose, workNote, actor, result, readBackNote))
        return result.await()
    }

    private suspend fun restore() {
        val active = trips.activeTrip() ?: return
        when (active.status) {
            TripStatus.DRIVING -> {
                _state.value = TripState.Driving(active.id)
                restoredLastSeenAt = active.lastSeenAt
            }
            TripStatus.PARKED -> {
                _state.value = TripState.Parked(active.id, active.endedAt ?: active.lastSeenAt)
                updateParkedTimer(isTick = false)
            }
            TripStatus.COMPLETED -> Unit
        }
    }

    private suspend fun handle(input: Input, vehicleId: String) {
        when (input) {
            is Input.Voice -> input.result.complete(
                try {
                    recordVoice(input)
                } catch (e: CancellationException) {
                    input.result.cancel()
                    throw e
                } catch (e: Exception) {
                    log("음성 기록 실패", e)
                    VoiceRecord.Failed
                }
            )
            is Input.Odometer -> {
                lastOdometerKm = input.km
                (state.value as? TripState.Driving)?.let { trips.touch(it.tripId, now()) }
            }
            is Input.Arrival -> activeTripId()?.let { trips.addStop(it, input.arrival, now()) }
            is Input.Event -> {
                val event = adjustForRestore(input.event)
                val transition = machine.reduce(state.value, event)
                _state.value = transition.state
                transition.effect?.let { apply(it, vehicleId) }
                updateParkedTimer(isTick = event is TripEvent.Tick)
            }
        }
    }

    private suspend fun apply(effect: TripEffect, vehicleId: String) {
        when (effect) {
            is TripEffect.StartTrip -> {
                // 먼저 저장하고(앱이 곧바로 죽어도 운행이 남도록) 느린 값은 이어서 채운다
                trips.startTrip(effect.tripId, vehicleId, effect.at, currentOdometerKm())
                val driver = currentDriver()
                if (driver != null) {
                    trips.setDriver(effect.tripId, driver, TripLedger.driverActor(driver.id), effect.at)
                    announce(TripAnnouncement.StartedBy(driver.name, driverNeedsConfirmation()))
                } else {
                    announce(TripAnnouncement.Started)
                }
                _moments.tryEmit(TripMoment.Started(effect.tripId))
                trips.updateStart(
                    effect.tripId,
                    readOrNull { vehicle.readBatterySocPercent() },
                    readOrNull { navi.currentPlace() }?.address
                )
            }
            is TripEffect.MarkParked -> {
                trips.markParked(effect.tripId, effect.at, currentOdometerKm())
                if (discardIfAccidental(effect.tripId)) return
                trips.updateEnd(
                    effect.tripId,
                    readOrNull { vehicle.readBatterySocPercent() },
                    readOrNull { navi.currentPlace() }?.address,
                    readOrNull { vehicle.readEfficiency() }
                )
                val parkedTrip = trips.activeTrip()?.takeIf { it.id == effect.tripId }
                val plannedPurpose = parkedTrip?.plannedPurpose
                _moments.tryEmit(TripMoment.Parked(effect.tripId, awaitingClassification = plannedPurpose == null))
                if (plannedPurpose != null) {
                    // 주행 중 음성으로 정한 용도로 바로 마친다 (분류 화면을 누르지 않아도 된다)
                    val actor = TripLedger.voiceActor(parkedTrip.driverId)
                    inputs.trySend(Input.Event(TripEvent.Classified(plannedPurpose, now(), actor)))
                } else {
                    announceParked(effect.tripId)
                }
            }
            is TripEffect.ResumeDriving -> {
                trips.resumeDriving(effect.tripId, now())
                _moments.tryEmit(TripMoment.Resumed(effect.tripId))
            }
            is TripEffect.CompleteTrip -> {
                // 말로 남긴 업무 내용은 분류 안내 때 되읽어 준다 (음성 인식이 잘못 받아 적었는지 운전자가 듣고 안다)
                val note = effect.purpose?.takeIf { it != TripPurpose.PERSONAL && effect.tripId != noteConfirmedTripId }
                    ?.let { trips.activeTrip()?.takeIf { trip -> trip.id == effect.tripId }?.workNote }
                noteConfirmedTripId = null
                trips.completeTrip(effect.tripId, effect.at)
                effect.purpose?.let {
                    trips.classify(effect.tripId, it, effect.actor, effect.at)
                    announce(TripAnnouncement.Classified(it, note))
                }
                _moments.tryEmit(TripMoment.Completed(effect.tripId, effect.purpose))
            }
        }
    }

    /**
     * 주차장에서 기어만 잠깐 바꾼 경우(거리 0, 1분 미만)는 운행이 아니므로 분류를 묻지 않고 버린다 (2026-10-27 점검).
     * 계기판이 1 km 단위인 차에서도 짧은 실제 운행을 잃지 않도록 시간 조건을 함께 본다. 계기판 값을 모르면 남긴다
     */
    private suspend fun discardIfAccidental(tripId: String): Boolean {
        val trip = trips.activeTrip()?.takeIf { it.id == tripId } ?: return false
        val start = trip.startOdometerKm ?: return false
        val end = trip.endOdometerKm ?: return false
        val parkedAt = trip.endedAt ?: return false
        if (kotlin.math.abs(end - start) >= ACCIDENTAL_MAX_KM || parkedAt - trip.startedAt >= ACCIDENTAL_MAX_MS) return false
        trips.discardTrip(tripId)
        _state.value = TripState.Idle
        return true
    }

    /** 화면을 보지 않아도 알 수 있게 거리·시간과 지난번에 이곳에서 고른 용도를 알린다 */
    private suspend fun announceParked(tripId: String) {
        val trip = trips.activeTrip()?.takeIf { it.id == tripId } ?: return
        val distanceKm = trip.startOdometerKm?.let { start -> trip.endOdometerKm?.let { (it - start).coerceAtLeast(0.0) } }
        val suggestion = trip.endAddress?.let { address -> readOrNull { trips.suggestPurpose(address) } }
        announce(TripAnnouncement.Parked(distanceKm, (trip.endedAt ?: now()) - trip.startedAt, suggestion))
    }

    private suspend fun recordVoice(input: Input.Voice): VoiceRecord = when (val current = state.value) {
        is TripState.Driving -> {
            trips.setPlan(current.tripId, input.purpose, input.workNote)
            VoiceRecord.PlannedWhileDriving
        }
        is TripState.Parked -> {
            trips.setPlan(current.tripId, input.purpose, input.workNote)
            noteConfirmedTripId = current.tripId.takeIf { !input.readBackNote }
            inputs.trySend(Input.Event(TripEvent.Classified(input.purpose, now(), input.actor)))
            VoiceRecord.Completed(current.tripId)
        }
        TripState.Idle -> {
            val last = trips.latestCompletedTrip()?.takeIf { now() - (it.endedAt ?: it.startedAt) <= RECENT_TRIP_MS }
            if (last == null) {
                VoiceRecord.NoTrip
            } else {
                trips.classify(last.id, input.purpose, input.actor, now())
                val note = input.workNote?.takeIf { input.purpose != TripPurpose.PERSONAL }
                note?.let { trips.setWorkNote(last.id, it, input.actor, now()) }
                announce(TripAnnouncement.Classified(input.purpose, note.takeIf { input.readBackNote }))
                VoiceRecord.Corrected(last.id)
            }
        }
    }

    private fun adjustForRestore(event: TripEvent): TripEvent {
        if (event !is TripEvent.GearChanged) return event
        val lastSeenAt = restoredLastSeenAt ?: return event
        restoredLastSeenAt = null
        return if (event.gear == GearPosition.PARK) event.copy(at = lastSeenAt) else event
    }

    /** 정차 중이면 대기 시간이 끝날 때 확인 신호를 보낸다 */
    private fun updateParkedTimer(isTick: Boolean) {
        val parked = state.value as? TripState.Parked
        if (parked == null) {
            parkedTimer?.cancel()
            parkedTimer = null
            return
        }
        if (parkedTimer?.isActive == true && !isTick) return
        parkedTimer?.cancel()
        parkedTimer = scope.launch {
            delay((parked.parkedAt + parkedTimeoutMs() - now()).coerceAtLeast(0))
            inputs.send(Input.Event(TripEvent.Tick(now())))
        }
    }

    private fun activeTripId(): String? = when (val current = state.value) {
        is TripState.Driving -> current.tripId
        is TripState.Parked -> current.tripId
        TripState.Idle -> null
    }

    /** 소수 첫째 자리까지 (Float 오차가 DB에 남지 않도록) */
    private suspend fun currentOdometerKm(): Double? =
        (lastOdometerKm ?: readOrNull { vehicle.readOdometerKm() })?.let { (it * 10).roundToLong() / 10.0 }

    private suspend fun <T> readOrNull(read: suspend () -> T?): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 차량이 지원하지 않는 값(예: 에뮬레이터의 전비)은 흔하므로 한 줄로만 남긴다
        Log.w(TAG, "차량·내비 값 읽기 실패: ${e.message}")
        null
    }

    private fun log(message: String, error: Throwable) {
        Log.w(TAG, message, error)
    }

    private sealed interface Input {
        data class Event(val event: TripEvent) : Input
        class Voice(
            val purpose: TripPurpose,
            val workNote: String?,
            val actor: String,
            val result: CompletableDeferred<VoiceRecord>,
            val readBackNote: Boolean
        ) : Input
        data class Odometer(val km: Float?) : Input
        data class Arrival(val arrival: NaviArrival) : Input
    }

    companion object {
        private const val TAG = "DriveLog/Recorder"

        /** M1은 차량마다 기본 운전자 1명 (M2에서 운전자 선택) */
        const val DRIVER_ACTOR = "driver:default"

        /** 음성으로 "방금 운행"을 고칠 수 있는 시간 */
        const val RECENT_TRIP_MS = 30 * 60_000L

        private const val MOMENT_BUFFER = 16

        /** 이보다 짧게 움직이고 짧게 달린 운행은 기어만 바꾼 것으로 본다 */
        private const val ACCIDENTAL_MAX_KM = 0.05
        private const val ACCIDENTAL_MAX_MS = 60_000L
    }
}

/** 음성으로 기록한 결과 */
sealed interface VoiceRecord {
    /** 주행 중이라 이번 운행에 적어 두었다. 정차하면 그 용도로 마친다 */
    data object PlannedWhileDriving : VoiceRecord

    /** 정차 중인 운행을 그 용도로 마쳤다 */
    data class Completed(val tripId: String) : VoiceRecord

    /** 방금 마친 운행을 그 용도로 고쳤다 */
    data class Corrected(val tripId: String) : VoiceRecord

    /** 고칠 운행이 없다 */
    data object NoTrip : VoiceRecord

    data object Failed : VoiceRecord
}
