package com.altrostudio.drivelog.domain.consent

import android.content.Context
import com.altrostudio.drivelog.data.settings.AppPreferences
import com.altrostudio.drivelog.data.trip.TripRepository
import com.altrostudio.drivelog.domain.trip.TripRecorder
import com.altrostudio.drivelog.service.TripRecorderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 개인정보 수집·이용 동의. 동의 버튼을 눌렀을 때만 [agree]가 불린다 (뒤로가기·빈 곳 터치는 동의가 아니다).
 * 철회하면 기록을 멈추고 저장된 데이터를 즉시 지운다.
 */
class ConsentManager(
    private val context: Context,
    private val preferences: AppPreferences,
    private val trips: TripRepository,
    private val recorder: TripRecorder,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _consentGiven = MutableStateFlow(preferences.consentGiven)
    val consentGiven: StateFlow<Boolean> = _consentGiven.asStateFlow()

    fun agree() {
        preferences.consentGiven = true
        preferences.consentGivenAt = now()
        preferences.consentVersion = CONSENT_VERSION
        _consentGiven.value = true
        TripRecorderService.start(context)
    }

    suspend fun withdraw() {
        TripRecorderService.stop(context)
        recorder.stop()
        trips.deleteAll()
        preferences.clearAll()
        _consentGiven.value = false
    }

    companion object {
        /** 동의문 내용을 바꾸면 올려서 다시 동의받는다 */
        const val CONSENT_VERSION = 1
    }
}
