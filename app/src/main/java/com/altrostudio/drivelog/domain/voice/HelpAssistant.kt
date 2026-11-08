package com.altrostudio.drivelog.domain.voice

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 도움 비서가 안내할 수 있는 일 (화면 이동과 함께) */
enum class HelpTopic {
    /** 차 상태 보기 (배터리·창문·문·실내등) */
    CAR_STATUS,

    /** 월별 운행기록부·이번 달 요약 */
    LOGBOOK,

    /** 휴대폰으로 받기 (QR) */
    SHARE,

    /** 운전하는 분 바꾸기 */
    DRIVER,

    /** 정차 후 말로 용도 기록하는 법 */
    VOICE_RECORD,

    /** 사용법 다시 보기 (튜토리얼) */
    TUTORIAL,

    /** 설정 (음성 안내·말로 기록·차 안 연동 켜고 끄기) */
    SETTINGS
}

/**
 * 도움 비서에게 한 말을 안내할 일로 바꾼다 (차 안 음성 엔진이 받아 적은 한국어, 띄어쓰기는 무시).
 * 여러 낱말이 섞이면 더 구체적인 일을 먼저 본다: "말로 기록 어떻게 해?"는 사용법이 아니라 말로 기록.
 */
object HelpIntentParser {

    fun parse(text: String): HelpTopic? {
        val compact = text.replace(Regex("[\\s.,!?~]"), "")
        if (compact.isEmpty()) return null
        return RULES.firstOrNull { (_, words) -> words.any { it in compact } }?.first
    }

    /** 위에서부터 먼저 맞춘다 */
    private val RULES = listOf(
        HelpTopic.SHARE to listOf("휴대폰", "핸드폰", "폰으로", "QR", "큐알", "공유", "보내", "내보내", "CSV", "엑셀", "받기"),
        HelpTopic.VOICE_RECORD to listOf("말로", "음성으로", "목소리", "용도", "분류", "업무내용"),
        HelpTopic.DRIVER to listOf("운전자", "운전하는", "누가운전", "기사"),
        HelpTopic.CAR_STATUS to listOf("차상태", "차량상태", "상태", "배터리", "충전", "창문", "문잠", "잠겼", "잠가", "실내등", "주행가능"),
        HelpTopic.LOGBOOK to listOf("운행기록부", "기록부", "이번달", "지난달", "얼마나", "몇킬로", "거리", "비율", "기록보여", "기록봐"),
        HelpTopic.SETTINGS to listOf("설정", "꺼줘", "켜줘", "끄고", "켜고", "음성안내"),
        HelpTopic.TUTORIAL to listOf("사용법", "처음", "튜토리얼", "도움말", "어떻게써", "어떻게사용", "설명", "뭐할수", "뭘할수")
    )
}

/** 도움 비서 상태 (홈의 [도움] 창) */
sealed interface HelpState {
    data object Idle : HelpState

    /** "무엇을 도와드릴까요?"를 읽는 중 */
    data object Asking : HelpState

    data class Listening(val partial: String) : HelpState

    /** 답을 읽었다. 화면은 [topic]으로 이동한다 */
    data class Answered(val topic: HelpTopic, val heard: String?) : HelpState

    data class NotUnderstood(val heard: String) : HelpState

    /** 말이 없었다. 아래 버튼으로 골라도 된다 */
    data object NoSpeech : HelpState

    data object Unavailable : HelpState
}

/**
 * 음성 도움 비서. 처음 쓰는 운전자가 화면을 뒤지지 않아도 "차 상태 알려 줘", "휴대폰으로 어떻게 받아?"처럼 물으면
 * 답을 소리로 읽고 그 화면으로 데려다준다. 차 안 음성 엔진(Pleos SpeechToText·TextToSpeech ON_DEVICE)만 쓰고,
 * 인터넷이나 생성형 AI 서버를 쓰지 않는다 (정해 둔 일 7가지 안에서 답한다). 받아 적은 글자는 저장하지 않는다.
 */
class HelpAssistant(
    private val listener: Listener,
    private val say: (String) -> Unit,
    private val awaitQuiet: suspend () -> Unit,
    private val stopSpeaking: () -> Unit,
    /** 일마다 소리로 읽을 답 (차 상태·이번 달 요약처럼 지금 값을 넣는다) */
    private val answer: suspend (HelpTopic) -> String,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<HelpState>(HelpState.Idle)
    val state: StateFlow<HelpState> = _state.asStateFlow()

    private var job: Job? = null

    /** [도움]을 눌렀다: 묻고 듣는다 */
    fun start() = launch {
        _state.value = HelpState.Asking
        say(ASK)
        awaitQuiet()
        delay(AFTER_SPEECH_MS)
        _state.value = HelpState.Listening("")
        when (val hearing = listener.listen(LISTEN_MS, onPartial = { _state.value = HelpState.Listening(it) })) {
            is Hearing.Heard -> {
                val topic = HelpIntentParser.parse(hearing.text)
                if (topic == null) {
                    _state.value = HelpState.NotUnderstood(hearing.text)
                    say(NOT_UNDERSTOOD)
                } else {
                    respond(topic, hearing.text)
                }
            }
            Hearing.Silence -> _state.value = HelpState.NoSpeech
            is Hearing.Unavailable -> _state.value = HelpState.Unavailable
        }
    }

    /** 창의 버튼으로 골랐다 */
    fun choose(topic: HelpTopic) = launch {
        listener.cancel()
        stopSpeaking()
        respond(topic, null)
    }

    /**
     * 듣기와 답을 멈춘다. 출발해서 닫을 때는 [stopSpeech]를 false로 둔다
     * (운행 시작 안내가 도움 안내를 덮어 읽으므로, 여기서 멈추면 그 안내까지 지워진다)
     */
    fun cancel(stopSpeech: Boolean = true) {
        job?.cancel()
        job = null
        listener.cancel()
        if (stopSpeech) stopSpeaking()
        _state.value = HelpState.Idle
    }

    private suspend fun respond(topic: HelpTopic, heard: String?) {
        val text = runCatching { answer(topic) }.getOrElse {
            Log.w(TAG, "도움 답 만들기 실패", it)
            ""
        }
        if (text.isNotBlank()) say(text)
        _state.value = HelpState.Answered(topic, heard)
    }

    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        listener.cancel()
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "도움 비서 실패", e)
                _state.value = HelpState.Idle
            }
        }
    }

    companion object {
        private const val TAG = "DriveLog/Help"
        const val ASK = "무엇을 도와드릴까요? 예를 들어 차 상태 알려 줘, 휴대폰으로 받는 법 알려 줘라고 말씀하세요."
        const val NOT_UNDERSTOOD = "잘 모르겠어요. 화면의 버튼에서 골라 주세요."
        const val LISTEN_MS = 8_000L
        const val AFTER_SPEECH_MS = 300L
    }
}

/** 도움 비서가 읽는 고정 답 (차 상태·운행기록부처럼 지금 값이 들어가는 답은 앱이 만든다) */
object HelpPhrases {
    const val SHARE = "운행기록부 화면의 휴대폰으로 받기를 누르면 QR 코드가 떠요. 휴대폰 카메라로 찍으면 그 기간의 운행기록부가 열리고, 십 분 뒤 닫혀요."
    const val SHARE_UNPAIRED = "회사에 연결하지 않은 차는 운행기록부 화면에서 CSV로 저장해요. 회사에 연결하면 휴대폰으로 받을 수 있어요."
    const val VOICE_RECORD = "차를 세우면 용도를 물어봐요. 업무, 거래처 미팅처럼 말하고, 차가 되읽으면 맞아라고 하거나 맞아요를 누르세요. 직접 입력도 돼요."
    const val DRIVER = "운전하는 분을 고를게요. 고르면 그분의 온도, 시트 열선, 무드등으로 차를 맞춰요."
    const val DRIVER_UNPAIRED = "운전자 고르기는 회사에 연결한 차에서 쓸 수 있어요. 설정에서 차량을 연결해 주세요."
    const val TUTORIAL = "사용법을 다시 보여 드릴게요."
    const val SETTINGS = "설정을 열게요. 음성 안내, 말로 기록, 차 안 연동을 켜고 끌 수 있어요."

    /** 이번 달 요약. 예: "이번 달 운행 12건, 모두 77.9킬로미터, 업무사용비율 100퍼센트예요. 운행기록부를 열게요." */
    fun logbook(tripCount: Int, totalKm: Double, workRatioPercent: Double?): String {
        if (tripCount == 0) return "이번 달 운행은 아직 없어요. 운행기록부를 열게요."
        val ratio = workRatioPercent?.let { "업무사용비율 ${oneDecimal(it)}퍼센트예요. " } ?: ""
        return "이번 달 운행 ${tripCount}건, 모두 ${oneDecimal(totalKm)}킬로미터, ${ratio}운행기록부를 열게요."
    }

    /** 12.0 → "12", 77.94 → "77.9" */
    private fun oneDecimal(value: Double): String {
        val tenths = Math.round(value * 10)
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
}
