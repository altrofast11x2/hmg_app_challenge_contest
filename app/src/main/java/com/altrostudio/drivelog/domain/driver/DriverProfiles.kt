package com.altrostudio.drivelog.domain.driver

import com.altrostudio.drivelog.data.driver.DriverDao
import com.altrostudio.drivelog.data.driver.DriverEntity
import com.altrostudio.drivelog.data.trip.DriverRef
import com.altrostudio.drivelog.data.trip.TripLedger
import com.altrostudio.drivelog.data.trip.TripRepository
import com.altrostudio.drivelog.data.vehicle.CabinControl
import com.altrostudio.drivelog.data.vehicle.Comfort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.max

/** 운전자 선택을 기억하는 값. 실제 구현은 AppPreferences */
interface DriverSettings {
    var currentDriverId: String?

    /** 운전자를 마지막으로 확인한 시각 */
    var driverConfirmedAt: Long

    /** 마지막 운행을 마친 시각 (운전 중인 사람이 그대로인지 판단) */
    var lastTripEndedAt: Long

    /** 운전자를 고르면 그 사람의 맞춤 설정(온도·열선·무드등)을 차에 적용한다. 처음에는 켜져 있다 */
    var autoComfort: Boolean
}

/** 운전자를 고른 방법 (수정 이력의 "바꾼 사람"에 남는다) */
enum class DriverSource { TOUCH, VOICE }

data class DriverProfile(
    val id: String,
    val name: String,
    val department: String?,
    val comfort: Comfort,
    /** 관리자가 이 차를 쓰도록 배정한 직원 */
    val assigned: Boolean
) {
    val ref: DriverRef get() = DriverRef(id, name, department)
    val label: String get() = ref.label
}

/** 운전자를 고른 결과. [applied]는 맞춤 설정 중 실제로 차에 적용된 것 */
data class DriverSelection(val driver: DriverProfile, val applied: Comfort)

/**
 * 공용차 운전자 프로필.
 * - 명단은 조직 서버에서 받는다 (승인된 직원 + 관리자가 추가한 운전자, 이 차에 배정된 사람이 위)
 * - 운전자를 고르면 운행기록부 ④사용자 칸에 적고, 그 사람의 맞춤 설정으로 차를 맞춘다
 * - 운행이 끝나면 차의 지금 설정을 그 사람의 맞춤 설정으로 기억한다 → 다음에 이 차나 회사의 다른 공용차에서 되살린다
 */
class DriverProfiles(
    private val dao: DriverDao,
    private val cabin: CabinControl,
    private val trips: TripRepository,
    private val settings: DriverSettings,
    paired: StateFlow<Boolean>,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis
) {
    val roster: StateFlow<List<DriverProfile>> = dao.observeDrivers()
        .map { list -> list.map { it.toProfile() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val currentId = MutableStateFlow(settings.currentDriverId)

    /** 운전자 기능을 쓰는지: 조직에 연결했고 명단이 있다 (연결하지 않은 개인 차량은 운전자를 묻지 않는다) */
    val enabled: StateFlow<Boolean> = combine(paired, roster) { isPaired, list -> isPaired && list.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 지금 운전자. 고르지 않았거나 명단에서 빠졌으면 null */
    val current: StateFlow<DriverProfile?> = combine(enabled, roster, currentId) { on, list, id ->
        if (on) list.firstOrNull { it.id == id } else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** 운행에 적을 지금 운전자 */
    fun currentRef(): DriverRef? = current.value?.ref

    /**
     * 운전자를 다시 확인할 때인지. 고른 적이 없거나, 마지막 확인·운행 뒤 [SESSION_GAP_MS]가 지나 다른 사람이 탔을 수 있을 때
     */
    fun needsConfirmation(at: Long = now()): Boolean =
        enabled.value && (current.value == null || at - max(settings.driverConfirmedAt, settings.lastTripEndedAt) > SESSION_GAP_MS)

    /**
     * 운전자를 고른다. 진행 중인 운행이 있으면 그 운행의 운전자도 바꾸고,
     * [applyComfort]면 그 사람의 맞춤 설정으로 차를 맞춘다 (정차 중 화면 누름 또는 음성으로만 부른다)
     */
    suspend fun select(driverId: String, source: DriverSource, applyComfort: Boolean = settings.autoComfort): DriverSelection? {
        val driver = roster.value.firstOrNull { it.id == driverId } ?: dao.driver(driverId)?.toProfile() ?: return null
        settings.currentDriverId = driver.id
        settings.driverConfirmedAt = now()
        currentId.value = driver.id
        trips.activeTrip()?.let { trips.setDriver(it.id, driver.ref, actor(source, driver.id), now()) }
        val applied = if (applyComfort && !driver.comfort.isEmpty) cabin.applyComfort(driver.comfort) else Comfort()
        return DriverSelection(driver, applied)
    }

    /** 음성으로 말한 이름에 맞는 운전자. 공백을 무시하고, 성을 빼고 불러도 찾는다 (예: "민준") */
    fun findByName(spoken: String): DriverProfile? {
        val key = spoken.normalizedName()
        if (key.isEmpty()) return null
        val list = roster.value
        return list.firstOrNull { it.name.normalizedName() == key }
            ?: list.filter { it.name.normalizedName().endsWith(key) || key.endsWith(it.name.normalizedName()) }.singleOrNull()
            ?: list.filter { key.contains(it.name.normalizedName()) }.singleOrNull()
    }

    /**
     * 운행이 끝날 때 차의 지금 설정을 지금 운전자의 맞춤 설정으로 기억한다. 읽지 못한 항목은 전에 기억한 값을 둔다.
     * 바뀌었으면 서버에도 올린다(다른 공용차에서도 같은 설정)
     */
    suspend fun learnComfort(observed: Comfort): Boolean {
        val driver = current.value ?: return false
        val merged = Comfort(
            tempC = observed.tempC ?: driver.comfort.tempC,
            seatHeat = observed.seatHeat ?: driver.comfort.seatHeat,
            ambientColor = observed.ambientColor ?: driver.comfort.ambientColor
        )
        if (merged == driver.comfort) return false
        dao.updateComfort(driver.id, merged.tempC, merged.seatHeat, merged.ambientColor, now())
        return true
    }

    fun onTripEnded() {
        settings.lastTripEndedAt = now()
    }

    fun setAutoComfort(on: Boolean) {
        settings.autoComfort = on
    }

    val autoComfort: Boolean get() = settings.autoComfort

    /** 조직 연결을 끊거나 동의를 철회하면 고른 운전자를 잊는다 */
    fun forget() {
        settings.currentDriverId = null
        settings.driverConfirmedAt = 0
        currentId.value = null
    }

    private fun actor(source: DriverSource, driverId: String) = when (source) {
        DriverSource.TOUCH -> TripLedger.driverActor(driverId)
        DriverSource.VOICE -> TripLedger.voiceActor(driverId)
    }

    private fun DriverEntity.toProfile() = DriverProfile(id, name, department, comfort, assigned)

    private fun String.normalizedName() = filterNot { it.isWhitespace() || it == '님' }.lowercase()

    companion object {
        /** 이만큼 쉬었다가 다시 타면 다른 사람이 탔을 수 있어 운전자를 다시 확인한다 */
        const val SESSION_GAP_MS = 2 * 60 * 60_000L
    }
}
