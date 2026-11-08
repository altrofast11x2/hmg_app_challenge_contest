package com.altrostudio.drivelog.domain.cabin

import android.util.Log
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.vehicle.AmbientLight
import com.altrostudio.drivelog.data.vehicle.CabinControl
import com.altrostudio.drivelog.data.vehicle.CabinWindow
import com.altrostudio.drivelog.data.vehicle.Comfort
import com.altrostudio.drivelog.data.vehicle.GearPosition
import com.altrostudio.drivelog.data.vehicle.VehicleDataSource
import com.altrostudio.drivelog.domain.driver.DriverProfiles
import com.altrostudio.drivelog.domain.trip.TripMoment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** 무드등 신호와 하차 점검 설정. 실제 구현은 AppPreferences */
interface CabinSettings {
    /** 정차 후 분류를 기다릴 때·분류했을 때 무드등 색으로 알린다. 처음에는 켜져 있다 */
    var ambientCue: Boolean

    /** 운행을 마치면 창문·실내등·배터리를 확인한다. 처음에는 켜져 있다 */
    var exitCheck: Boolean

    /** 신호로 바꾸기 전의 무드등 (앱이 신호 도중 꺼져도 다음에 되돌린다) */
    var savedAmbient: AmbientLight?
}

/** 무드등 신호 색 (0xRRGGBB). 디자인 토큰의 용도 색과 같은 값을 넣는다 */
data class AmbientPalette(val pending: Int, val commute: Int, val business: Int, val personal: Int) {
    fun of(purpose: TripPurpose): Int? = when (purpose) {
        TripPurpose.COMMUTE -> commute
        TripPurpose.BUSINESS -> business
        TripPurpose.PERSONAL -> personal
        TripPurpose.UNCLASSIFIED -> null
    }
}

/** [한 번에 정리]로 처리하는 항목 */
enum class TidyItem { WINDOWS, LIGHTS, DOORS }

/**
 * 차 상태 보기 화면에 보여 줄 지금 차 상태. 읽지 못한(지원하지 않는) 값은 null.
 * [canControl]이면 P이고 주행 제한이 없어 창문 닫기·문 잠그기·실내등 끄기를 할 수 있다
 */
data class CarStatus(
    val readAt: Long,
    val socPercent: Int?,
    val odometerKm: Float?,
    val openWindows: Set<CabinWindow>?,
    val doorsLocked: Boolean?,
    val cabinLightOn: Boolean?,
    val comfort: Comfort,
    val canControl: Boolean,
    val busy: Boolean = false,
    /** 마지막으로 눌렀는데 차가 바꾸지 못한 항목 */
    val failed: Set<TidyItem> = emptySet()
) {
    val windowsOpen: Boolean get() = !openWindows.isNullOrEmpty()

    /** 지금 할 수 있는 정리 */
    val actions: Set<TidyItem>
        get() = buildSet {
            if (windowsOpen) add(TidyItem.WINDOWS)
            if (doorsLocked == false) add(TidyItem.DOORS)
            if (cabinLightOn == true) add(TidyItem.LIGHTS)
        }
}

/** 운행을 마친 뒤의 차 상태 (하차 전 확인 카드, 관리자 웹 차량 현황) */
data class ExitCheck(
    val tripId: String,
    val checkedAt: Long,
    val openWindows: Set<CabinWindow>,
    val cabinLightOn: Boolean,
    val doorsLocked: Boolean?,
    val socPercent: Int?,
    val tidied: Set<TidyItem> = emptySet(),
    val tidying: Boolean = false,
    /** 정리하려 했지만 차가 받아들이지 않은 항목 */
    val failed: Set<TidyItem> = emptySet()
) {
    val needsTidy: Boolean get() = openWindows.isNotEmpty() || cabinLightOn
    val lowBattery: Boolean get() = socPercent != null && socPercent < LOW_BATTERY_PERCENT

    /** 카드를 보여 줄 만한 내용이 있는지 */
    val hasNotice: Boolean get() = needsTidy || lowBattery || tidied.isNotEmpty() || failed.isNotEmpty()

    companion object {
        const val LOW_BATTERY_PERCENT = 20
    }
}

/** 관리자 웹 "차량 현황"에 올리는 운행 종료 시점의 차 상태 */
data class VehicleStatusReport(
    val reportedAt: Long,
    val tripId: String?,
    val driverId: String?,
    val driverName: String?,
    val odometerKm: Double?,
    val socPercent: Int?,
    val openWindows: Set<CabinWindow>,
    val doorsLocked: Boolean?,
    val cabinLightOn: Boolean?,
    val tidied: Set<TidyItem>
)

/**
 * 차 안 연동. 운행 순간([TripMoment])을 듣고 차를 움직인다.
 * - 무드등 신호: 정차 후 분류를 기다리면 경고색, 분류하면 용도 색으로 잠깐 바꾼 뒤 원래대로 되돌린다
 * - 운전자 설정 기억: 정차할 때 차의 온도·열선·무드등을 지금 운전자의 맞춤 설정으로 기억한다
 * - 하차 점검: 운행을 마치면 열린 창문·켜진 실내등·배터리를 확인해 알리고, 운전자가 누르면 한 번에 정리한다
 *
 * 안전: 창문·실내등·문 잠금은 기어가 P이고 주행 제한이 없을 때 운전자가 직접 눌렀을 때만 바꾼다.
 * 무드등 신호와 설정 기억은 운전에 영향이 없는 조명·읽기라 자동으로 한다.
 */
class CabinAssistant(
    private val cabin: CabinControl,
    private val vehicle: VehicleDataSource,
    private val drivers: DriverProfiles,
    private val settings: CabinSettings,
    private val palette: AmbientPalette,
    private val say: (String) -> Unit,
    private val report: suspend (VehicleStatusReport) -> Unit,
    /** 운전자 맞춤 설정이 바뀌어 서버에 올릴 것이 생겼다 */
    private val onComfortChanged: () -> Unit = {},
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val flashMs: Long = FLASH_MS,
    private val noticeDelayMs: Long = NOTICE_DELAY_MS
) {
    private val mutex = Mutex()
    private val _exitCheck = MutableStateFlow<ExitCheck?>(null)

    /** 마지막 운행의 하차 점검 (다음 운행을 시작하면 지운다) */
    val exitCheck: StateFlow<ExitCheck?> = _exitCheck.asStateFlow()

    private var job: Job? = null

    /** [moments]를 듣기 시작한다. 지난번 신호 도중 앱이 꺼졌으면 무드등부터 되돌린다 */
    fun start(moments: Flow<TripMoment>, parkedTripExists: suspend () -> Boolean) {
        if (job != null) return
        job = scope.launch {
            if (settings.savedAmbient != null && !parkedTripExists()) mutex.withLock { restoreAmbient() }
            moments.collect { moment ->
                try {
                    mutex.withLock { handle(moment) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "차 안 연동 실패: ${moment::class.simpleName}", e)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** 하차 전 확인 카드를 닫는다 */
    fun dismissExitCheck() {
        _exitCheck.value = null
    }

    /**
     * 운전자가 [한 번에 정리](또는 항목 하나)를 눌렀다. P가 아니거나 주행 제한 중이면 아무것도 하지 않는다.
     * 정리한 뒤 다시 읽어 확인하고 관리자 웹 차량 현황도 고쳐 올린다
     */
    suspend fun tidyUp(items: Set<TidyItem>): ExitCheck? {
        val check = _exitCheck.value ?: return null
        if (!safeToControl()) return check
        _exitCheck.value = check.copy(tidying = true)
        return mutex.withLock {
            val done = mutableSetOf<TidyItem>()
            val failed = mutableSetOf<TidyItem>()
            if (TidyItem.WINDOWS in items && check.openWindows.isNotEmpty()) {
                if (cabin.closeWindows()) done += TidyItem.WINDOWS else failed += TidyItem.WINDOWS
            }
            if (TidyItem.LIGHTS in items && check.cabinLightOn) {
                if (cabin.turnOffCabinLight()) done += TidyItem.LIGHTS else failed += TidyItem.LIGHTS
            }
            if (TidyItem.DOORS in items && check.doorsLocked != true) {
                if (cabin.lockDoors()) done += TidyItem.DOORS else failed += TidyItem.DOORS
            }
            val state = cabin.readCabin()
            val updated = check.copy(
                openWindows = state.openWindows ?: check.openWindows,
                cabinLightOn = state.cabinLightOn ?: check.cabinLightOn,
                doorsLocked = state.doorsLocked ?: check.doorsLocked,
                tidied = check.tidied + done,
                failed = failed,
                tidying = false
            )
            _exitCheck.value = updated
            if (done.isNotEmpty()) say(CabinPhrases.tidied(done))
            send(updated)
            updated
        }
    }

    /** 차 상태 보기: 배터리·계기판·창문·문·실내등·온도·열선·무드등을 한 번에 읽는다 (읽기만, 차를 바꾸지 않는다) */
    suspend fun readStatus(): CarStatus {
        val state = runCatching { cabin.readCabin() }.getOrNull()
        return CarStatus(
            readAt = now(),
            socPercent = runCatching { vehicle.readBatterySocPercent() }.getOrNull(),
            odometerKm = runCatching { vehicle.readOdometerKm() }.getOrNull(),
            openWindows = state?.openWindows,
            doorsLocked = state?.doorsLocked,
            cabinLightOn = state?.cabinLightOn,
            comfort = runCatching { cabin.readComfort() }.getOrNull() ?: Comfort(),
            canControl = safeToControl()
        )
    }

    /**
     * 차 상태 보기의 [창문 닫기]·[문 잠그기]·[실내등 끄기]. 하차 점검과 같은 안전 조건(P, 주행 제한 없음)에서만 바꾸고,
     * 바꾼 뒤 다시 읽어 확인한다. 하차 점검 카드가 떠 있으면 그 카드도 고친다
     */
    suspend fun control(items: Set<TidyItem>): CarStatus {
        if (!safeToControl()) return readStatus()
        val done = mutableSetOf<TidyItem>()
        val failed = mutableSetOf<TidyItem>()
        mutex.withLock {
            val state = cabin.readCabin()
            if (TidyItem.WINDOWS in items && !state.openWindows.isNullOrEmpty()) {
                if (cabin.closeWindows()) done += TidyItem.WINDOWS else failed += TidyItem.WINDOWS
            }
            if (TidyItem.LIGHTS in items && state.cabinLightOn == true) {
                if (cabin.turnOffCabinLight()) done += TidyItem.LIGHTS else failed += TidyItem.LIGHTS
            }
            if (TidyItem.DOORS in items && state.doorsLocked == false) {
                if (cabin.lockDoors()) done += TidyItem.DOORS else failed += TidyItem.DOORS
            }
        }
        val status = readStatus().copy(failed = failed)
        _exitCheck.value?.let { check ->
            val updated = check.copy(
                openWindows = status.openWindows ?: check.openWindows,
                cabinLightOn = status.cabinLightOn ?: check.cabinLightOn,
                doorsLocked = status.doorsLocked ?: check.doorsLocked,
                tidied = check.tidied + done
            )
            _exitCheck.value = updated
            if (done.isNotEmpty()) send(updated)
        }
        if (done.isNotEmpty()) say(CabinPhrases.tidied(done))
        return status
    }

    /**
     * 정차 화면에서 [나중에]를 눌렀다. 곧 내릴 수 있으니 분류를 기다리지 않고 하차 점검을 한다
     * (기다리면 5분 뒤 미분류로 끝날 때에야 점검 카드가 떠서 이미 내린 뒤가 된다)
     */
    fun checkBeforeLeaving(tripId: String) {
        if (!settings.exitCheck) return
        scope.launch {
            try {
                mutex.withLock { checkCabin(tripId, announce = true, classified = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "하차 점검 실패: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun handle(moment: TripMoment) {
        when (moment) {
            is TripMoment.Started -> {
                _exitCheck.value = null
                restoreAmbient()
            }
            is TripMoment.Parked -> {
                rememberComfort()
                if (moment.awaitingClassification && settings.ambientCue) showCue(palette.pending)
            }
            is TripMoment.Resumed -> restoreAmbient()
            is TripMoment.Completed -> {
                drivers.onTripEnded()
                val color = moment.purpose?.let(palette::of)
                if (color != null && settings.ambientCue) {
                    showCue(color)
                    delay(flashMs)
                }
                restoreAmbient()
                if (settings.exitCheck) checkCabin(moment.tripId, announce = true, classified = moment.purpose != null)
            }
        }
    }

    /** 지금 차 설정을 운전자의 맞춤 설정으로 기억한다. 무드등이 신호 색이면 신호 전 색을 쓴다 */
    private suspend fun rememberComfort() {
        if (drivers.current.value == null) return
        val observed = cabin.readComfort()
        val saved = settings.savedAmbient
        if (drivers.learnComfort(if (saved != null) observed.copy(ambientColor = saved.color) else observed)) onComfortChanged()
    }

    private suspend fun showCue(color: Int) {
        if (settings.savedAmbient == null) {
            settings.savedAmbient = cabin.readAmbient() ?: return
        }
        cabin.showAmbient(color, CUE_MIN_BRIGHTNESS)
    }

    private suspend fun restoreAmbient() {
        val saved = settings.savedAmbient ?: return
        cabin.restoreAmbient(saved)
        settings.savedAmbient = null
    }

    private suspend fun checkCabin(tripId: String, announce: Boolean, classified: Boolean) {
        val state = cabin.readCabin()
        val soc = runCatching { vehicle.readBatterySocPercent() }.getOrNull()
        val check = ExitCheck(
            tripId = tripId,
            checkedAt = now(),
            openWindows = state.openWindows.orEmpty(),
            cabinLightOn = state.cabinLightOn == true,
            doorsLocked = state.doorsLocked,
            socPercent = soc
        )
        _exitCheck.value = check.takeIf { it.hasNotice }
        send(check)
        if (announce && (check.needsTidy || check.lowBattery)) {
            // 분류 결과 안내("일반업무로 기록했습니다")를 끊지 않도록 조금 뒤에 읽는다. 그동안 정리 버튼은 바로 누를 수 있다
            scope.launch {
                if (classified) delay(noticeDelayMs)
                if (_exitCheck.value?.tripId == tripId && _exitCheck.value?.tidied.isNullOrEmpty()) say(CabinPhrases.notice(check))
            }
        }
    }

    private suspend fun send(check: ExitCheck) {
        val driver = drivers.current.value
        val odometer = runCatching { vehicle.readOdometerKm() }.getOrNull()?.let { (it * 10).roundToInt() / 10.0 }
        runCatching {
            report(
                VehicleStatusReport(
                    reportedAt = now(),
                    tripId = check.tripId,
                    driverId = driver?.id,
                    driverName = driver?.name,
                    odometerKm = odometer,
                    socPercent = check.socPercent,
                    openWindows = check.openWindows,
                    doorsLocked = check.doorsLocked,
                    cabinLightOn = check.cabinLightOn,
                    tidied = check.tidied
                )
            )
        }.onFailure { Log.w(TAG, "차량 상태 저장 실패", it) }
    }

    private suspend fun safeToControl(): Boolean {
        val gear = withTimeoutOrNull(READ_TIMEOUT_MS) { vehicle.gear.first() }
        val restricted = withTimeoutOrNull(READ_TIMEOUT_MS) { vehicle.drivingRestricted.first() } ?: true
        return gear == GearPosition.PARK && !restricted
    }

    companion object {
        private const val TAG = "DriveLog/Cabin"

        /** 분류한 용도 색을 보여 주는 시간 */
        const val FLASH_MS = 2_500L

        /** 분류 결과 안내가 끝나기를 기다리는 시간 */
        const val NOTICE_DELAY_MS = 2_500L

        /** 무드등이 너무 어두우면 신호가 보이지 않아 이만큼은 밝힌다 (0~100) */
        const val CUE_MIN_BRIGHTNESS = 60

        private const val READ_TIMEOUT_MS = 2_000L
    }
}

/** 하차 점검·운전자 맞춤 안내 문장 */
object CabinPhrases {

    /** 예: "김민준 님 설정으로 맞췄어요. 온도 21.5도, 시트 열선 2단계." */
    fun applied(driverName: String, applied: com.altrostudio.drivelog.data.vehicle.Comfort): String {
        if (applied.isEmpty) return "$driverName 님으로 기록할게요."
        val parts = buildList {
            applied.tempC?.let { add("온도 ${temperature(it)}도") }
            applied.seatHeat?.let { level ->
                when {
                    level > 0 -> add("시트 열선 ${level}단계")
                    level < 0 -> add("시트 통풍 ${-level}단계")
                    else -> add("시트 열선 끔")
                }
            }
            if (applied.ambientColor != null) add("무드등")
        }
        return "$driverName 님 설정으로 맞췄어요. ${parts.joinToString(", ")}."
    }

    /** 21.0 → "21", 21.5 → "21.5" */
    fun temperature(celsius: Float): String {
        val tenths = Math.round(celsius * 10)
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    fun notice(check: ExitCheck): String = buildString {
        append("내리기 전에 확인해 주세요. ")
        if (check.openWindows.isNotEmpty()) append(windowsSubject(check.openWindows)).append(" 열려 있어요. ")
        if (check.cabinLightOn) append("실내등이 켜져 있어요. ")
        if (check.lowBattery) append("배터리가 ${check.socPercent}퍼센트예요. 다음 분을 위해 충전해 주세요. ")
        if (check.needsTidy) append("화면에서 한 번에 정리할 수 있어요.")
    }.trim()

    /**
     * 차 상태를 소리로 읽을 문장 (도움 비서의 "차 상태 알려 줘").
     * 예: "배터리 77퍼센트, 계기판 426킬로미터입니다. 창문은 모두 닫혀 있고, 문은 잠겨 있고, 실내등은 꺼져 있어요."
     */
    fun status(status: CarStatus): String {
        val numbers = buildList {
            status.socPercent?.let { add("배터리 ${it}퍼센트") }
            status.odometerKm?.let { add("계기판 ${Math.round(it)}킬로미터") }
        }
        val cabin = buildList {
            status.openWindows?.let { open -> add(if (open.isEmpty()) "창문은 모두 닫혀 있고" else "${windowsSubject(open)} 열려 있고") }
            status.doorsLocked?.let { add(if (it) "문은 잠겨 있고" else "문은 잠기지 않았고") }
            status.cabinLightOn?.let { add(if (it) "실내등은 켜져 있어요" else "실내등은 꺼져 있어요") }
        }
        val first = if (numbers.isEmpty()) "" else numbers.joinToString(", ") + "입니다. "
        val second = when {
            cabin.isEmpty() -> ""
            cabin.last().endsWith("고") -> cabin.joinToString(", ").removeSuffix("고") + "어요."
            else -> cabin.joinToString(", ") + "."
        }
        val tail = if (status.actions.isNotEmpty() && status.canControl) " 차 상태 화면에서 바로 정리할 수 있어요." else ""
        return (first + second + tail).trim().ifEmpty { "차 상태를 읽지 못했어요." }
    }

    /** 예: "창문을 닫고 실내등을 껐습니다." */
    fun tidied(items: Set<TidyItem>): String {
        val steps = TidyItem.entries.filter { it in items }.map { STEPS.getValue(it) }
        if (steps.isEmpty()) return ""
        return (steps.dropLast(1).map { it.first } + steps.last().second).joinToString(" ") + "."
    }

    /** 주어 + 조사: "운전석 창문이", "창문 2개가" */
    fun windowsSubject(open: Set<CabinWindow>): String = when {
        open.size == 1 -> windowName(open.first()) + "이"
        else -> "창문 ${open.size}개가"
    }

    fun windowName(window: CabinWindow): String = when (window) {
        CabinWindow.FRONT_LEFT -> "운전석 창문"
        CabinWindow.FRONT_RIGHT -> "조수석 창문"
        CabinWindow.REAR_LEFT -> "뒷좌석 왼쪽 창문"
        CabinWindow.REAR_RIGHT -> "뒷좌석 오른쪽 창문"
    }

    /** 이어 말할 때와 끝낼 때 */
    private val STEPS = mapOf(
        TidyItem.WINDOWS to ("창문을 닫고" to "창문을 닫았습니다"),
        TidyItem.LIGHTS to ("실내등을 끄고" to "실내등을 껐습니다"),
        TidyItem.DOORS to ("문을 잠그고" to "문을 잠갔습니다")
    )
}
