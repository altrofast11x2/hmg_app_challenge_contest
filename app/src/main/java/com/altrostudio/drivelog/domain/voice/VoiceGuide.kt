package com.altrostudio.drivelog.domain.voice

import com.altrostudio.drivelog.data.trip.TripPurpose
import kotlin.math.roundToLong

/** 운전자가 화면을 보지 않아도 알 수 있게 소리로 알리는 운행 순간 */
sealed interface TripAnnouncement {
    /** 기어를 D로 바꿔 기록을 시작했다 */
    data object Started : TripAnnouncement

    /** 공용차에서 [driverName] 님으로 기록을 시작했다. [confirm]이면 다른 사람일 때 바꾸는 방법을 알린다 */
    data class StartedBy(val driverName: String, val confirm: Boolean) : TripAnnouncement

    /** P로 정차했다. 분류를 기다린다. [suggestion]은 같은 곳에서 지난번에 고른 용도 */
    data class Parked(val distanceKm: Double?, val durationMs: Long, val suggestion: TripPurpose?) : TripAnnouncement

    /** 정차 후 운전자가 용도를 골랐다. [workNote]는 말로 남긴 업무 내용 (되읽어 확인시킨다) */
    data class Classified(val purpose: TripPurpose, val workNote: String? = null) : TripAnnouncement
}

/** 소리로 읽어 주는 장치 (차량에서는 Pleos TextToSpeech) */
interface Speaker {
    fun speak(text: String)
    fun stop()
}

interface VoiceSettings {
    var voiceGuidance: Boolean
}

/** 운행 순간을 한국어 문장으로 바꿔 읽어 준다. 설정에서 끄면 읽지 않는다 */
class VoiceGuide(
    private val speaker: Speaker,
    private val settings: VoiceSettings,
    /** 정차 안내 끝에 용도를 말로 물을지 ([VoiceJournal]) */
    private val asksAfterParking: () -> Boolean = { false }
) {

    val enabled: Boolean get() = settings.voiceGuidance

    fun setEnabled(on: Boolean) {
        settings.voiceGuidance = on
        if (on) speaker.speak(VoicePhrases.ENABLED) else speaker.stop()
    }

    fun announce(announcement: TripAnnouncement) {
        if (settings.voiceGuidance) speaker.speak(VoicePhrases.of(announcement, asking = asksAfterParking()))
    }

    /** 운행 밖의 안내 (운전자 맞춤, 하차 점검). 설정에서 끄면 읽지 않는다 */
    fun say(text: String) {
        if (settings.voiceGuidance && text.isNotBlank()) speaker.speak(text)
    }

    /** 읽던 안내를 멈춘다 (사용법을 건너뛸 때) */
    fun stop() = speaker.stop()
}

/** 읽어 줄 문장. 운전 중에 듣기 쉽게 짧게, 숫자는 단위를 붙여 소리 나는 대로 쓴다 */
object VoicePhrases {

    const val ENABLED = "음성 안내를 켰습니다. 운행을 시작하고 마칠 때 알려 드립니다."

    /** 말로 들은 용도를 알아듣지 못했을 때 한 번 다시 묻는다 */
    const val ASK_AGAIN = "출퇴근, 업무, 비업무 중에 말씀해 주세요."

    const val PICK_ON_SCREEN = "알아듣지 못했습니다. 화면에서 용도를 골라 주세요."

    /** 일반업무인데 업무 내용을 말하지 않았을 때 한 번 더 묻는다 */
    const val ASK_NOTE = "어떤 업무였는지 말씀해 주세요."

    /** 확인에서 "아니"라고 했을 때 */
    const val SAY_AGAIN = "다시 말씀해 주세요."

    /** 확인 대답을 알아듣지 못했을 때 */
    const val CONFIRM_AGAIN = "맞으면 맞아, 다르면 아니라고 말씀해 주세요."

    /** 기록하기 전에 들은 용도와 업무 내용을 되읽고 맞는지 묻는다 */
    fun confirm(purpose: TripPurpose, workNote: String?): String = buildString {
        append(
            when (purpose) {
                TripPurpose.COMMUTE -> "출퇴근"
                TripPurpose.BUSINESS -> "일반업무"
                TripPurpose.PERSONAL -> "비업무"
                TripPurpose.UNCLASSIFIED -> "미분류"
            }
        )
        workNote?.takeIf { it.isNotBlank() && purpose != TripPurpose.PERSONAL }?.let {
            append(", 업무 내용 ").append(it.take(NOTE_READ_MAX))
        }
        append(". 맞으면 맞아, 다르면 다시 말씀해 주세요.")
    }

    /** [asking]이면 정차 안내 끝에 용도를 말로 묻는다 (말로 기록) */
    fun of(announcement: TripAnnouncement, asking: Boolean = false): String = when (announcement) {
        TripAnnouncement.Started -> "운행 기록을 시작합니다."
        is TripAnnouncement.StartedBy ->
            if (announcement.confirm) {
                "${announcement.driverName} 님으로 운행 기록을 시작합니다. 다른 분이면 정차한 뒤 화면에서 바꿔 주세요."
            } else {
                "${announcement.driverName} 님, 운행 기록을 시작합니다."
            }
        is TripAnnouncement.Parked -> buildString {
            append("운행을 마쳤습니다. ")
            val distance = distance(announcement.distanceKm)
            append(if (distance != null) "$distance, " else "")
            append(duration(announcement.durationMs)).append(" 운행했습니다. ")
            val suggested = announcement.suggestion?.let(::withParticle)
            suggested?.let { append("이곳은 지난번에 ").append(it).append(" 기록했습니다. ") }
            append(
                when {
                    !asking -> "화면에서 용도를 골라 주세요."
                    suggested != null -> "맞으면 네, 아니면 용도를 말씀해 주세요."
                    else -> "용도와 업무 내용을 말씀해 주세요."
                }
            )
        }
        is TripAnnouncement.Classified -> buildString {
            append(withParticle(announcement.purpose) ?: "미분류로").append(" 기록했습니다.")
            announcement.workNote?.takeIf { it.isNotBlank() && announcement.purpose != TripPurpose.PERSONAL }?.let {
                append(" 업무 내용, ").append(it.take(NOTE_READ_MAX)).append('.')
            }
        }
    }

    /** 되읽는 업무 내용 최대 글자 수 (운전 중에 듣기 길지 않게) */
    private const val NOTE_READ_MAX = 60

    /** 0.1 km 단위. 움직이지 않았으면 말하지 않는다 */
    private fun distance(km: Double?): String? {
        val tenths = km?.let { (it * 10).roundToLong() }?.takeIf { it > 0 } ?: return null
        return if (tenths % 10 == 0L) "${tenths / 10}킬로미터" else "${tenths / 10}.${tenths % 10}킬로미터"
    }

    private fun duration(ms: Long): String {
        val minutes = (ms + 30_000) / 60_000
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            minutes < 1 -> "1분 미만"
            hours == 0L -> "${minutes}분"
            rest == 0L -> "${hours}시간"
            else -> "${hours}시간 ${rest}분"
        }
    }

    /** 받침에 맞춘 "~(으)로" */
    private fun withParticle(purpose: TripPurpose): String? = when (purpose) {
        TripPurpose.COMMUTE -> "출퇴근으로"
        TripPurpose.BUSINESS -> "일반업무로"
        TripPurpose.PERSONAL -> "비업무로"
        TripPurpose.UNCLASSIFIED -> null
    }
}
