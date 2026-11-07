package com.altrostudio.drivelog.data.settings

import android.content.Context
import androidx.core.content.edit
import com.altrostudio.drivelog.data.remote.DeviceSession
import com.altrostudio.drivelog.data.remote.RemoteVehicleStatus
import com.altrostudio.drivelog.data.security.KeystoreCipher
import com.altrostudio.drivelog.data.security.SecretStore
import com.altrostudio.drivelog.data.security.StringCipher
import com.altrostudio.drivelog.data.sync.CabinSyncPrefs
import com.altrostudio.drivelog.data.sync.Pairing
import com.altrostudio.drivelog.data.sync.SyncSettings
import com.altrostudio.drivelog.data.vehicle.AmbientLight
import com.altrostudio.drivelog.domain.cabin.CabinSettings
import com.altrostudio.drivelog.domain.driver.DriverSettings
import com.altrostudio.drivelog.domain.update.UpdateSettings
import com.altrostudio.drivelog.domain.voice.VoiceJournalSettings
import com.altrostudio.drivelog.domain.voice.VoiceSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom

/**
 * 기기에 저장하는 간단한 설정.
 * 차량 ID·VIN·기기 계정 토큰·조직 연결·DB 키는 Keystore 키로 암호화해 저장한다 (Pleos 앱 보안 가이드)
 */
class AppPreferences(context: Context, cipher: StringCipher = KeystoreCipher()) :
    SyncSettings, VoiceSettings, VoiceJournalSettings, UpdateSettings, DriverSettings, CabinSettings, CabinSyncPrefs {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val secrets = SecretStore(cipher, read = { prefs.getString(it, null) }, write = { key, value -> prefs.edit { putString(key, value) } })

    /** 개인정보 수집·이용 동의. 동의 버튼을 눌렀을 때만 true가 된다 */
    var consentGiven: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(value) = prefs.edit { putBoolean(KEY_CONSENT, value) }

    /** 동의한 시각과 동의문 버전 (동의 증빙) */
    var consentGivenAt: Long
        get() = prefs.getLong(KEY_CONSENT_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_CONSENT_AT, value) }

    var consentVersion: Int
        get() = prefs.getInt(KEY_CONSENT_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_CONSENT_VERSION, value) }

    var vehicleId: String?
        get() = secrets.get(KEY_VEHICLE_ID)
        set(value) = secrets.put(KEY_VEHICLE_ID, value)

    override var vin: String?
        get() = secrets.get(KEY_VIN)
        set(value) = secrets.put(KEY_VIN, value)

    /** 정차(P)가 이 시간 동안 이어지면 미분류로 운행을 마친다 */
    var parkedTimeoutMs: Long
        get() = prefs.getLong(KEY_PARKED_TIMEOUT, DEFAULT_PARKED_TIMEOUT_MS)
        set(value) = prefs.edit { putLong(KEY_PARKED_TIMEOUT, value) }

    /** 운행 시작·정차·분류를 소리로 알린다. 처음에는 켜져 있다 */
    override var voiceGuidance: Boolean
        get() = prefs.getBoolean(KEY_VOICE_GUIDANCE, true)
        set(value) = prefs.edit { putBoolean(KEY_VOICE_GUIDANCE, value) }

    /** 사용법(튜토리얼)을 끝까지 보거나 건너뛰었다 */
    var tutorialSeen: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_TUTORIAL_SEEN, value) }

    /** 정차하면 용도와 업무 내용을 말로 묻고 듣는다. 처음에는 켜져 있다 */
    override var voiceJournal: Boolean
        get() = prefs.getBoolean(KEY_VOICE_JOURNAL, true)
        set(value) = prefs.edit { putBoolean(KEY_VOICE_JOURNAL, value) }

    override var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_UPDATE_CHECK_AT, value) }

    /** 차량용 기기 계정의 로그인 세션 */
    override var deviceSession: DeviceSession?
        get() = secrets.get(KEY_DEVICE_SESSION)?.let { runCatching { json.decodeFromString<DeviceSession>(it) }.getOrNull() }
        set(value) = secrets.put(KEY_DEVICE_SESSION, value?.let { json.encodeToString(it) })

    /** 연결된 조직. null이면 아직 연결하지 않았다 */
    override var pairing: Pairing?
        get() = secrets.get(KEY_PAIRING)?.let { runCatching { json.decodeFromString<Pairing>(it) }.getOrNull() }
        set(value) = secrets.put(KEY_PAIRING, value?.let { json.encodeToString(it) })

    /** 연결 화면에서 [나중에 연결]을 골랐다 */
    override var pairingSkipped: Boolean
        get() = prefs.getBoolean(KEY_PAIRING_SKIPPED, false)
        set(value) = prefs.edit { putBoolean(KEY_PAIRING_SKIPPED, value) }

    // ─── 공용차 운전자 ──────────────────────────────────────────

    override var currentDriverId: String?
        get() = prefs.getString(KEY_CURRENT_DRIVER, null)
        set(value) = prefs.edit { putString(KEY_CURRENT_DRIVER, value) }

    override var driverConfirmedAt: Long
        get() = prefs.getLong(KEY_DRIVER_CONFIRMED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_DRIVER_CONFIRMED_AT, value) }

    override var lastTripEndedAt: Long
        get() = prefs.getLong(KEY_LAST_TRIP_ENDED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_TRIP_ENDED_AT, value) }

    override var autoComfort: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COMFORT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_COMFORT, value) }

    // ─── 무드등 신호·하차 점검 ─────────────────────────────────────

    override var ambientCue: Boolean
        get() = prefs.getBoolean(KEY_AMBIENT_CUE, true)
        set(value) = prefs.edit { putBoolean(KEY_AMBIENT_CUE, value) }

    override var exitCheck: Boolean
        get() = prefs.getBoolean(KEY_EXIT_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_EXIT_CHECK, value) }

    /** "색|밝기|켜짐" (값이 없으면 빈칸) */
    override var savedAmbient: AmbientLight?
        get() = prefs.getString(KEY_SAVED_AMBIENT, null)?.split('|')?.takeIf { it.size == 3 }?.let { (color, brightness, on) ->
            AmbientLight(color.toIntOrNull(), brightness.toIntOrNull(), on.toBooleanStrictOrNull())
        }
        set(value) = prefs.edit {
            putString(KEY_SAVED_AMBIENT, value?.let { listOf(it.color ?: "", it.brightness ?: "", it.on ?: "").joinToString("|") })
        }

    override var pendingStatus: RemoteVehicleStatus?
        get() = secrets.get(KEY_PENDING_STATUS)?.let { runCatching { json.decodeFromString<RemoteVehicleStatus>(it) }.getOrNull() }
        set(value) = secrets.put(KEY_PENDING_STATUS, value?.let { json.encodeToString(it) })

    override var rosterFetchedAt: Long
        get() = prefs.getLong(KEY_ROSTER_FETCHED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_ROSTER_FETCHED_AT, value) }

    /**
     * 운행 기록 DB 암호화 키 (32바이트 난수의 16진수). 처음 부를 때 만든다.
     * 동의 철회로 설정을 지워도 남긴다 (DB 파일은 남고 내용만 지워지므로, 키를 지우면 다시 열 수 없다)
     */
    @Synchronized
    fun databasePassphrase(): String = secrets.get(KEY_DB_KEY) ?: run {
        val bytes = ByteArray(DB_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        bytes.joinToString("") { "%02x".format(it) }.also { secrets.put(KEY_DB_KEY, it) }
    }

    /** 동의를 철회하면 저장된 설정(차량 ID, VIN 포함)을 모두 지운다. DB 키만 남긴다 */
    fun clearAll() {
        val dbKey = prefs.getString(KEY_DB_KEY, null)
        prefs.edit {
            clear()
            putString(KEY_DB_KEY, dbKey)
        }
    }

    companion object {
        const val DEFAULT_PARKED_TIMEOUT_MS = 5 * 60_000L
        private const val FILE_NAME = "drivelog"
        private const val KEY_CONSENT = "consent_given"
        private const val KEY_CONSENT_AT = "consent_given_at"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_VEHICLE_ID = "vehicle_id"
        private const val KEY_VIN = "vin"
        private const val KEY_PARKED_TIMEOUT = "parked_timeout_ms"
        private const val KEY_VOICE_GUIDANCE = "voice_guidance"
        private const val KEY_VOICE_JOURNAL = "voice_journal"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_DB_KEY = "database_key"
        private const val KEY_UPDATE_CHECK_AT = "update_check_at"
        private const val DB_KEY_BYTES = 32
        private const val KEY_DEVICE_SESSION = "device_session"
        private const val KEY_PAIRING = "pairing"
        private const val KEY_PAIRING_SKIPPED = "pairing_skipped"
        private const val KEY_CURRENT_DRIVER = "current_driver"
        private const val KEY_DRIVER_CONFIRMED_AT = "driver_confirmed_at"
        private const val KEY_LAST_TRIP_ENDED_AT = "last_trip_ended_at"
        private const val KEY_AUTO_COMFORT = "auto_comfort"
        private const val KEY_AMBIENT_CUE = "ambient_cue"
        private const val KEY_EXIT_CHECK = "exit_check"
        private const val KEY_SAVED_AMBIENT = "saved_ambient"
        private const val KEY_PENDING_STATUS = "pending_status"
        private const val KEY_ROSTER_FETCHED_AT = "roster_fetched_at"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
