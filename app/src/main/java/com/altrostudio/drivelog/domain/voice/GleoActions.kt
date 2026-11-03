package com.altrostudio.drivelog.domain.voice

import android.content.Context
import android.content.Intent
import android.util.Log
import com.altrostudio.drivelog.data.trip.TripLedger
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.data.trip.TripRepository
import com.altrostudio.drivelog.domain.driver.DriverProfiles
import com.altrostudio.drivelog.domain.driver.DriverSource
import com.altrostudio.drivelog.domain.logbook.LogbookBuilder
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import com.altrostudio.drivelog.domain.trip.TripRecorder
import com.altrostudio.drivelog.domain.trip.VoiceRecord
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Gleo AI에 돌려주는 답 (Pleos 앱 연동 가이드의 Callback Schema). message는 영어 문장 */
data class GleoResponse(
    val functionName: String,
    val status: Status,
    val message: String,
    val data: JSONArray? = null,
    /** 앱이 직접 소리로 알릴 때는 BEEP (Gleo가 같은 내용을 또 읽지 않게) */
    val mode: Mode = Mode.VOICE
) {
    enum class Status(val wire: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }
    enum class Mode(val wire: String) { VOICE("voice"), BEEP("beep"), SILENT("silent") }

    fun toJson(): JSONObject = JSONObject().apply {
        put("functionName", functionName)
        put("status", status.wire)
        put("message", message)
        put("mode", mode.wire)
        data?.let { put("data", it.toString()) }
    }
}

/**
 * Gleo AI 음성 명령 (assets/schema.json의 함수 세 개).
 * - RECORD_TRIP_PURPOSE: "방금 운행 거래처 미팅으로 기록해" → 용도 + 업무 내용. 주행 중이면 정차할 때 그 용도로 마친다
 * - SET_DRIVER: "운전자 김민준으로 바꿔" → 운행기록부 사용자 칸과 그 사람의 온도·열선·무드등
 * - GET_DRIVING_SUMMARY: "이번 달 업무용으로 얼마나 탔어?" → 기록부 요약
 * Gleo는 7초 뒤 기다리기를 멈추므로 6초 안에 답한다.
 */
class GleoActions(
    private val context: Context,
    private val recorder: TripRecorder,
    private val trips: TripRepository,
    private val drivers: DriverProfiles,
    private val consentGiven: StateFlow<Boolean>,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {

    suspend fun handle(action: String, rawParams: String?): GleoResponse {
        val params = parseParams(rawParams)
        return withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
            if (!consentGiven.value) {
                GleoResponse(action, GleoResponse.Status.FAIL, "The vehicle logbook has not been activated on this car yet.")
            } else {
                when (action) {
                    RECORD_TRIP_PURPOSE -> recordPurpose(params)
                    SET_DRIVER -> setDriver(params)
                    GET_DRIVING_SUMMARY -> summary(params)
                    else -> GleoResponse(action, GleoResponse.Status.FAIL, "This function is not supported.")
                }
            }
        } ?: GleoResponse(action, GleoResponse.Status.FAIL, "A timeout occurred while processing the request.")
    }

    /** Gleo AI가 받는 방송으로 답을 보낸다 */
    fun respond(response: GleoResponse) {
        runCatching {
            context.sendBroadcast(
                Intent(RESPONSE_ACTION)
                    .putExtra("packageName", context.packageName)
                    .putExtra("response", response.toJson().toString())
            )
        }.onFailure { Log.w(TAG, "Gleo 응답 실패", it) }
    }

    private suspend fun recordPurpose(params: JSONObject): GleoResponse {
        val purpose = when (params.optString("purpose").uppercase(Locale.ROOT)) {
            "COMMUTE" -> TripPurpose.COMMUTE
            "BUSINESS" -> TripPurpose.BUSINESS
            "PERSONAL" -> TripPurpose.PERSONAL
            else -> return GleoResponse(
                RECORD_TRIP_PURPOSE,
                GleoResponse.Status.ASKING,
                "The trip purpose is required: commute, business, or personal.",
                JSONArray().put(JSONObject().put("missingField", "purpose"))
            )
        }
        val note = params.optString("workNote").takeIf { it.isNotBlank() && purpose != TripPurpose.PERSONAL }
        val actor = TripLedger.voiceActor(drivers.current.value?.id)
        val data = JSONArray().put(JSONObject().put("purpose", purpose.name).apply { note?.let { put("workNote", it) } })
        return when (val result = recorder.recordByVoice(purpose, note, actor)) {
            VoiceRecord.PlannedWhileDriving -> GleoResponse(
                RECORD_TRIP_PURPOSE,
                GleoResponse.Status.SUCCESS,
                "Saved. This trip will be recorded as ${purpose.english} when the car is parked.",
                data
            )
            // 앱이 "일반업무로 기록했습니다"를 직접 읽으므로 Gleo는 효과음만 낸다
            is VoiceRecord.Completed -> GleoResponse(
                RECORD_TRIP_PURPOSE, GleoResponse.Status.SUCCESS, "The trip was recorded as ${purpose.english}.", data, GleoResponse.Mode.BEEP
            )
            is VoiceRecord.Corrected -> GleoResponse(
                RECORD_TRIP_PURPOSE, GleoResponse.Status.SUCCESS, "The last trip was changed to ${purpose.english}.", data, GleoResponse.Mode.BEEP
            )
            VoiceRecord.NoTrip -> GleoResponse(RECORD_TRIP_PURPOSE, GleoResponse.Status.FAIL, "There is no recent trip to record.")
            VoiceRecord.Failed -> GleoResponse(RECORD_TRIP_PURPOSE, GleoResponse.Status.FAIL, "An unexpected internal error occurred.")
        }
    }

    private suspend fun setDriver(params: JSONObject): GleoResponse {
        if (!drivers.enabled.value) {
            return GleoResponse(SET_DRIVER, GleoResponse.Status.FAIL, "Driver profiles are available only when this car is connected to a company.")
        }
        val spoken = params.optString("driverName")
        val names = JSONArray().apply { drivers.roster.value.forEach { put(JSONObject().put("driverName", it.name).put("department", it.department ?: "")) } }
        if (spoken.isBlank()) {
            return GleoResponse(SET_DRIVER, GleoResponse.Status.ASKING, "Please tell me the name of the driver.", names)
        }
        val driver = drivers.findByName(spoken)
            ?: return GleoResponse(SET_DRIVER, GleoResponse.Status.ASKING, "No driver named $spoken was found. Please choose one of the drivers.", names)
        val selection = drivers.select(driver.id, DriverSource.VOICE) ?: return GleoResponse(
            SET_DRIVER, GleoResponse.Status.FAIL, "The driver could not be selected."
        )
        val applied = selection.applied
        val data = JSONObject().put("driverName", driver.name).put("department", driver.department ?: "")
        applied.tempC?.let { data.put("temperatureCelsius", it.toDouble()) }
        applied.seatHeat?.let { data.put("seatHeatLevel", it) }
        val comfortPart = if (applied.isEmpty) "" else " and the saved comfort settings were applied"
        return GleoResponse(SET_DRIVER, GleoResponse.Status.SUCCESS, "The driver is now ${driver.name}$comfortPart.", JSONArray().put(data))
    }

    private suspend fun summary(params: JSONObject): GleoResponse {
        val zone = zone()
        val today = LocalDate.now(zone)
        val key = params.optString("period").uppercase(Locale.ROOT).ifBlank { "THIS_MONTH" }
        val period = when (key) {
            "TODAY" -> LogbookPeriod(today, today)
            "LAST_MONTH" -> LogbookPeriod.ofMonth(YearMonth.from(today).minusMonths(1))
            else -> LogbookPeriod.ofMonth(YearMonth.from(today))
        }
        val (from, to) = period.range(zone)
        val summary = LogbookBuilder.build(period, trips.observeCompletedTrips(from, to).first(), zone, driverName = "").summary
        val data = JSONObject()
            .put("period", key)
            .put("trips", summary.tripCount)
            .put("totalKm", summary.totalKm.oneDecimal())
            .put("businessKm", summary.businessKm.oneDecimal())
            .put("commuteKm", summary.commuteKm.oneDecimal())
            .put("unclassifiedTrips", summary.unclassifiedCount)
        summary.workRatioPercent?.let { data.put("businessUseRatioPercent", it.oneDecimal()) }
        return GleoResponse(GET_DRIVING_SUMMARY, GleoResponse.Status.SUCCESS, "Here is the driving summary from the vehicle logbook.", JSONArray().put(data))
    }

    private val TripPurpose.english: String
        get() = when (this) {
            TripPurpose.COMMUTE -> "commuting"
            TripPurpose.BUSINESS -> "business use"
            TripPurpose.PERSONAL -> "personal use"
            TripPurpose.UNCLASSIFIED -> "unclassified"
        }

    private fun Double.oneDecimal(): Double = (this * 10).roundToInt() / 10.0

    companion object {
        private const val TAG = "DriveLog/Gleo"
        const val RECORD_TRIP_PURPOSE = "RECORD_TRIP_PURPOSE"
        const val SET_DRIVER = "SET_DRIVER"
        const val GET_DRIVING_SUMMARY = "GET_DRIVING_SUMMARY"
        val ACTIONS = setOf(RECORD_TRIP_PURPOSE, SET_DRIVER, GET_DRIVING_SUMMARY)

        /** Gleo AI가 앱의 답을 받는 방송 */
        const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

        private const val ANSWER_TIMEOUT_MS = 6_000L

        /**
         * ACTION_PARAM은 JSON 문자열이다. 가이드 예시처럼 따옴표로 한 번 더 감싸 오는 경우도 받는다
         * (예: "\"{\"title\":\"미팅\"}\"")
         */
        fun parseParams(raw: String?): JSONObject {
            if (raw.isNullOrBlank()) return JSONObject()
            val text = raw.trim()
            runCatching { return JSONObject(text) }
            if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
                val inner = text.substring(1, text.length - 1).replace("\\\"", "\"")
                runCatching { return JSONObject(inner) }
            }
            return JSONObject()
        }
    }
}
