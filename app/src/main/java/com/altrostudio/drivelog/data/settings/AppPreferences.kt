package com.altrostudio.drivelog.data.settings

import android.content.Context
import androidx.core.content.edit

/** 기기에 저장하는 간단한 설정 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

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
        get() = prefs.getString(KEY_VEHICLE_ID, null)
        set(value) = prefs.edit { putString(KEY_VEHICLE_ID, value) }

    var vin: String?
        get() = prefs.getString(KEY_VIN, null)
        set(value) = prefs.edit { putString(KEY_VIN, value) }

    /** 정차(P)가 이 시간 동안 이어지면 미분류로 운행을 마친다 */
    var parkedTimeoutMs: Long
        get() = prefs.getLong(KEY_PARKED_TIMEOUT, DEFAULT_PARKED_TIMEOUT_MS)
        set(value) = prefs.edit { putLong(KEY_PARKED_TIMEOUT, value) }

    /** 동의를 철회하면 저장된 설정(차량 ID, VIN 포함)을 모두 지운다 */
    fun clearAll() = prefs.edit { clear() }

    companion object {
        const val DEFAULT_PARKED_TIMEOUT_MS = 5 * 60_000L
        private const val FILE_NAME = "drivelog"
        private const val KEY_CONSENT = "consent_given"
        private const val KEY_CONSENT_AT = "consent_given_at"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_VEHICLE_ID = "vehicle_id"
        private const val KEY_VIN = "vin"
        private const val KEY_PARKED_TIMEOUT = "parked_timeout_ms"
    }
}
