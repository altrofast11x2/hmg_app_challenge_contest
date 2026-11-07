package com.altrostudio.drivelog

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.altrostudio.drivelog.data.DriveLogDatabase
import com.altrostudio.drivelog.data.export.CsvFileSaver
import com.altrostudio.drivelog.data.navi.NaviDataSource
import com.altrostudio.drivelog.data.navi.PleosNaviDataSource
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.OkHttpBackendApi
import com.altrostudio.drivelog.data.remote.RemoteVehicleStatus
import com.altrostudio.drivelog.data.remote.SupabaseConfig
import com.altrostudio.drivelog.data.settings.AppPreferences
import com.altrostudio.drivelog.data.settings.VehicleIdentity
import com.altrostudio.drivelog.data.sync.CabinSync
import com.altrostudio.drivelog.data.sync.PairingRepository
import com.altrostudio.drivelog.data.sync.RoomCabinSyncStore
import com.altrostudio.drivelog.data.sync.ShareLinkRepository
import com.altrostudio.drivelog.data.sync.SyncRepository
import com.altrostudio.drivelog.data.sync.SyncScheduler
import com.altrostudio.drivelog.data.trip.RoomTripRepository
import com.altrostudio.drivelog.data.trip.TripLedger
import com.altrostudio.drivelog.data.trip.TripRepository
import com.altrostudio.drivelog.data.trip.TripStatus
import com.altrostudio.drivelog.data.update.WebLatestVersionSource
import com.altrostudio.drivelog.data.vehicle.CabinControl
import com.altrostudio.drivelog.data.vehicle.NoCabinControl
import com.altrostudio.drivelog.data.vehicle.PleosCabinControl
import com.altrostudio.drivelog.data.vehicle.PleosVehicleDataSource
import com.altrostudio.drivelog.data.vehicle.VehicleDataSource
import com.altrostudio.drivelog.data.voice.PleosListener
import com.altrostudio.drivelog.data.voice.PleosSpeaker
import com.altrostudio.drivelog.domain.cabin.AmbientPalette
import com.altrostudio.drivelog.domain.cabin.CabinAssistant
import com.altrostudio.drivelog.domain.cabin.VehicleStatusReport
import com.altrostudio.drivelog.domain.consent.ConsentManager
import com.altrostudio.drivelog.domain.driver.DriverProfiles
import com.altrostudio.drivelog.domain.trip.TripRecorder
import com.altrostudio.drivelog.domain.update.UpdateChecker
import com.altrostudio.drivelog.domain.cabin.CabinPhrases
import com.altrostudio.drivelog.domain.logbook.LogbookBuilder
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import com.altrostudio.drivelog.domain.voice.GleoActions
import com.altrostudio.drivelog.domain.voice.HelpAssistant
import com.altrostudio.drivelog.domain.voice.HelpPhrases
import com.altrostudio.drivelog.domain.voice.HelpTopic
import com.altrostudio.drivelog.domain.voice.TripAnnouncement
import com.altrostudio.drivelog.domain.voice.VoiceGuide
import com.altrostudio.drivelog.domain.voice.VoiceJournal
import com.altrostudio.drivelog.ui.theme.DriveLogDarkColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 앱 전체에서 하나씩만 쓰는 객체를 만든다 (수동 의존성 주입) */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(appContext)

    private val pleosVehicle by lazy { PleosVehicleDataSource(appContext) }

    val vehicle: VehicleDataSource get() = pleosVehicle

    /** 차량 제어 (공조·시트 열선·무드등·창문·실내등·문 잠금). 차량 OS가 아니면 아무것도 하지 않는다 */
    val cabin: CabinControl by lazy { if (pleosVehicle.isAutomotive) PleosCabinControl(pleosVehicle.sdk) else NoCabinControl }

    val navi: NaviDataSource by lazy { PleosNaviDataSource(appContext) }

    private val database by lazy { DriveLogDatabase.create(appContext) { preferences.databasePassphrase() } }

    val trips: TripRepository by lazy { RoomTripRepository(database) }

    val vehicleIdentity by lazy { VehicleIdentity(preferences, vehicle) }

    private val backendConfig = SupabaseConfig(
        appContext.getString(R.string.supabase_url),
        appContext.getString(R.string.supabase_anon_key)
    )

    private val backendApi by lazy { OkHttpBackendApi(backendConfig) }

    private val deviceAuth by lazy { DeviceAuth(backendApi, preferences) }

    val pairing by lazy {
        PairingRepository(backendApi, deviceAuth, preferences, vehicleIdentity::vehicleId, trips, backendConfig.isConfigured)
    }

    private val cabinSync by lazy { CabinSync(backendApi, RoomCabinSyncStore(database.driverDao(), preferences)) }

    val sync by lazy {
        SyncRepository(
            backendApi,
            deviceAuth,
            pairing.pairing,
            trips,
            vehicleIdentity::vehicleId,
            onUnpaired = {
                pairing.forget()
                forgetDrivers()
            },
            cabinSync = cabinSync
        )
    }

    val shareLinks by lazy {
        ShareLinkRepository(backendApi, deviceAuth, appContext.getString(R.string.web_url), syncFirst = { sync.sync() })
    }

    val syncScheduler by lazy { SyncScheduler(appContext, trips, pairing.pairing, appScope) }

    val consent by lazy { ConsentManager(appContext, preferences, trips, recorder, pairing) }

    val csvSaver by lazy { CsvFileSaver(appContext) }

    private val speaker by lazy { PleosSpeaker(appContext) }

    val voiceGuide: VoiceGuide by lazy { VoiceGuide(speaker, preferences, asksAfterParking = { voiceJournal.asksAfterParking }) }

    /**
     * 말로 용도·업무 내용 기록. Gleo AI 스키마 승인이 없어도 앱이 Pleos SpeechToText(차 안 엔진)로 직접 듣는다.
     * 승인되면 Gleo 명령([gleoActions])과 함께 쓴다
     */
    private val voiceListener by lazy { PleosListener(appContext) }

    val voiceJournal: VoiceJournal by lazy {
        VoiceJournal(
            listener = voiceListener,
            tripState = recorder.state,
            settings = preferences,
            guidanceOn = { voiceGuide.enabled },
            say = voiceGuide::say,
            awaitQuiet = speaker::awaitQuiet,
            stopSpeaking = speaker::stop,
            suggestion = { tripId -> trips.activeTrip()?.takeIf { it.id == tripId }?.endAddress?.let { trips.suggestPurpose(it) } },
            // 기록 전에 확인 질문으로 되읽었으므로 분류 안내는 용도만 읽는다
            record = { purpose, note ->
                recorder.recordByVoice(purpose, note, TripLedger.voiceActor(drivers.current.value?.id), readBackNote = false)
            },
            scope = appScope
        )
    }

    val updateChecker by lazy {
        val installed = appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode.toInt()
        UpdateChecker(WebLatestVersionSource(appContext.getString(R.string.web_url))::fetch, installed, preferences)
    }

    /** 공용차 운전자 (조직 명단, 지금 운전자, 맞춤 설정) */
    val drivers by lazy {
        val paired = pairing.pairing.map { it != null }.stateIn(appScope, SharingStarted.Eagerly, pairing.pairing.value != null)
        DriverProfiles(database.driverDao(), cabin, trips, preferences, paired, appScope)
    }

    /** 무드등 신호·하차 점검·운전자 설정 기억 */
    val cabinAssistant by lazy {
        val colors = DriveLogDarkColors
        CabinAssistant(
            cabin = cabin,
            vehicle = vehicle,
            drivers = drivers,
            settings = preferences,
            // 무드등 색은 디자인 토큰의 용도 색과 같다 (분류 대기는 경고색)
            palette = AmbientPalette(
                pending = colors.statusWarning.toArgb() and RGB,
                commute = colors.purposeCommuteBackground.toArgb() and RGB,
                business = colors.purposeBusinessBackground.toArgb() and RGB,
                personal = colors.purposePersonalBackground.toArgb() and RGB
            ),
            say = voiceGuide::say,
            report = ::queueStatusReport,
            onComfortChanged = { syncScheduler.requestSync() },
            scope = appScope
        )
    }

    /**
     * 음성 도움 비서 (홈의 [도움]). 처음 쓰는 운전자가 "차 상태 알려 줘"처럼 물으면 답하고 그 화면으로 데려다준다.
     * 차 안 음성 엔진만 쓰고 정해 둔 일 안에서 답한다 (인터넷·생성형 AI 서버 없음)
     */
    val helpAssistant: HelpAssistant by lazy {
        HelpAssistant(
            listener = voiceListener,
            say = voiceGuide::say,
            awaitQuiet = speaker::awaitQuiet,
            stopSpeaking = speaker::stop,
            answer = { topic -> helpAnswer(topic) },
            scope = appScope
        )
    }

    private suspend fun helpAnswer(topic: HelpTopic): String = when (topic) {
        HelpTopic.CAR_STATUS -> CabinPhrases.status(cabinAssistant.readStatus())
        HelpTopic.LOGBOOK -> {
            val zone = java.time.ZoneId.systemDefault()
            val period = LogbookPeriod.ofMonth(java.time.YearMonth.now(zone))
            val (from, to) = period.range(zone)
            val summary = LogbookBuilder.build(period, trips.observeCompletedTrips(from, to).first(), zone, driverName = "").summary
            HelpPhrases.logbook(summary.tripCount, summary.totalKm, summary.workRatioPercent)
        }
        HelpTopic.SHARE -> if (pairing.pairing.value != null) HelpPhrases.SHARE else HelpPhrases.SHARE_UNPAIRED
        HelpTopic.DRIVER -> if (drivers.enabled.value) HelpPhrases.DRIVER else HelpPhrases.DRIVER_UNPAIRED
        HelpTopic.VOICE_RECORD -> HelpPhrases.VOICE_RECORD
        HelpTopic.TUTORIAL -> HelpPhrases.TUTORIAL
        HelpTopic.SETTINGS -> HelpPhrases.SETTINGS
    }

    /** Gleo AI 음성 명령 (운행 용도·업무 내용 기록, 운전자 바꾸기, 운행 요약) */
    val gleoActions by lazy { GleoActions(appContext, recorder, trips, drivers, consent.consentGiven) }

    val recorder: TripRecorder by lazy {
        TripRecorder(
            vehicle = vehicle,
            navi = navi,
            trips = trips,
            vehicleId = vehicleIdentity::vehicleId,
            parkedTimeoutMs = { preferences.parkedTimeoutMs },
            scope = appScope,
            announce = { announcement ->
                voiceGuide.announce(announcement)
                // 정차 안내 끝에 용도를 물었으면 안내가 끝난 뒤 듣는다
                if (announcement is TripAnnouncement.Parked) voiceJournal.onParkedAnnounced()
            },
            currentDriver = { drivers.currentRef() },
            driverNeedsConfirmation = { drivers.needsConfirmation() }
        )
    }

    /** 앱이 시작될 때: 동기화 예약, 조직에 새로 연결되면 운전자 명단을 바로 받는다 */
    fun startBackground() {
        syncScheduler.start()
        var wasPaired = pairing.pairing.value != null
        pairing.pairing.onEach { current ->
            if (current != null && !wasPaired) {
                cabinSync.refreshRosterSoon()
                syncScheduler.requestSync()
            }
            wasPaired = current != null
        }.launchIn(appScope)
        // 앱을 켤 때 명단·맞춤 설정·차량 상태를 한 번 맞춘다 (올릴 운행이 없어도)
        if (wasPaired) syncScheduler.requestSync()
    }

    /** 동의한 뒤 기록 서비스가 부른다: 운행 감지와 차 안 연동을 함께 시작한다 */
    fun startRecording() {
        // 지난 실행에서 듣던 중 앱이 죽었으면 음성 서비스가 마이크를 계속 열고 있을 수 있다
        if (voiceJournal.enabled) appScope.launch { voiceListener.stopLeftover() }
        recorder.start()
        cabinAssistant.start(recorder.moments) { trips.activeTrip()?.status == TripStatus.PARKED }
    }

    /** 조직 연결이 끊기면 운전자 명단과 고른 운전자를 잊는다 (다른 기업의 직원 이름을 남기지 않는다) */
    private fun forgetDrivers() {
        drivers.forget()
        appScope.launch { database.driverDao().deleteAll() }
    }

    /** 조직에 연결한 차만 관리자 웹 차량 현황에 올린다 */
    private suspend fun queueStatusReport(report: VehicleStatusReport) {
        if (pairing.pairing.value == null) return
        preferences.pendingStatus = RemoteVehicleStatus(
            reportedAtMs = report.reportedAt,
            tripId = report.tripId,
            driverId = report.driverId,
            driverName = report.driverName,
            odometerKm = report.odometerKm,
            socPercent = report.socPercent,
            openWindows = report.openWindows.map { it.name }.sorted(),
            doorsLocked = report.doorsLocked,
            cabinLightOn = report.cabinLightOn,
            tidied = report.tidied.map { it.name }.sorted()
        )
        syncScheduler.requestSync()
    }

    private companion object {
        const val RGB = 0xFFFFFF
    }
}
