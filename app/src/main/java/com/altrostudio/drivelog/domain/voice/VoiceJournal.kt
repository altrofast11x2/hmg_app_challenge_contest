package com.altrostudio.drivelog.domain.voice

import android.util.Log
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.domain.trip.TripState
import com.altrostudio.drivelog.domain.trip.VoiceRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 차 안 음성 인식 (차량에서는 Pleos SpeechToText, 차 안 엔진만 씀) */
interface Listener {
    /**
     * 한 번 듣고 받아 적은 글자를 돌려준다. [onPartial]은 받아 적는 중간 글자(화면 표시용).
     * [audio]를 주면 마이크 대신 그 소리(16 kHz·16비트·모노 PCM)를 인식한다 (에뮬레이터 시험용).
     */
    suspend fun listen(maxMs: Long, onPartial: (String) -> Unit, audio: ByteArray? = null): Hearing

    /** 듣는 중이면 멈춘다 */
    fun cancel()
}

sealed interface Hearing {
    data class Heard(val text: String) : Hearing

    /** 아무 말도 듣지 못했다 */
    data object Silence : Hearing

    /** 음성 인식을 쓸 수 없다 (마이크 오류, 통화 중 등). [permanent]면 앱을 다시 켤 때까지 자동으로 묻지 않는다 */
    data class Unavailable(val reason: String, val permanent: Boolean) : Hearing
}

/** 분류 화면에 보여 줄 말로 기록 상태 */
sealed interface VoiceInputState {
    data object Idle : VoiceInputState

    /** 정차 안내를 읽는 중. 끝나면 듣는다 */
    data object Preparing : VoiceInputState

    /** 듣는 중. [forNote]면 일반업무의 업무 내용을 따로 묻고 듣는 중 */
    data class Listening(val partial: String, val forNote: Boolean = false) : VoiceInputState

    /**
     * 들은 내용이 맞는지 묻는 중. 운전자가 "맞아"·"다음"이라고 하거나 [맞아요]를 누르면 기록한다.
     * [listening]이면 대답을 듣는 중이고 [partial]은 받아 적는 대답
     */
    data class Confirming(
        val purpose: TripPurpose,
        val workNote: String?,
        val listening: Boolean = false,
        val partial: String = ""
    ) : VoiceInputState

    /** 확인한 내용으로 기록하는 중 */
    data class Recording(val purpose: TripPurpose, val workNote: String?) : VoiceInputState

    /** 용도를 알아듣지 못했다 */
    data class NotUnderstood(val heard: String) : VoiceInputState

    /** 버튼을 눌렀는데 아무 말도 듣지 못했다 */
    data object NoSpeech : VoiceInputState

    data object Unavailable : VoiceInputState
}

interface VoiceJournalSettings {
    /** 정차하면 용도와 업무 내용을 말로 물어본다. 처음에는 켜져 있다 */
    var voiceJournal: Boolean
}

/**
 * 말로 용도·업무 내용 기록 (Gleo AI 스키마 승인 없이 앱이 직접 듣는다).
 *
 * 정차 안내 끝에 "용도와 업무 내용을 말씀해 주세요"라고 묻고, 안내가 끝나면 듣는다.
 * "업무, 거래처 납품 미팅"이라고 하면 "일반업무, 업무 내용 거래처 납품 미팅. 맞으면 맞아, 다르면 다시 말씀해 주세요."라고
 * 되묻고, "맞아"(또는 "다음", 화면의 [맞아요])라고 하면 그때 기록한다. 음성 인식이 잘못 받아 적은 채로 넘어가지 않도록
 * 기록 전에 반드시 확인한다 (2026-11-05 사용자 요청: 사람 목소리 시험에서 "업무"가 "어 모어"로 바뀌어 기록됨).
 * - 일반업무인데 업무 내용이 없으면 "어떤 업무였는지 말씀해 주세요"라고 한 번 더 묻는다
 * - 확인 중 "아니"라고 하면 다시 듣고, 다른 내용을 말하면 그 내용으로 다시 확인한다. 용도 버튼을 누르면 그 용도 + 들은 업무 내용으로 기록
 * - 알아듣지 못하면 다시 묻고, 여러 번 모르면 화면에서 고르게 한다
 * - 자동으로 물었는데 말이 없으면 조용히 끝낸다. 확인 대답이 없으면 확인 화면을 남겨 버튼으로 고를 수 있게 한다
 * - 듣는 동안 운전자가 화면에서 고르거나 다시 출발하면 바로 멈춘다
 * 녹음은 앱이 받지 않는다. 차 안 음성 엔진이 받아 적은 글자만 받아 해석하고, 그 글자도 업무 내용 말고는 저장하지 않는다.
 * Gleo AI 스키마가 승인되면 "운행기록부, …" 명령([GleoActions])도 함께 쓸 수 있다.
 */
class VoiceJournal(
    private val listener: Listener,
    private val tripState: StateFlow<TripState>,
    private val settings: VoiceJournalSettings,
    /** 음성 안내가 켜져 있는지. 꺼져 있으면 자동으로 묻지 않는다 */
    private val guidanceOn: () -> Boolean,
    private val say: (String) -> Unit,
    /** 읽고 있는 안내가 끝날 때까지 기다린다 (안내 소리를 받아 적지 않도록) */
    private val awaitQuiet: suspend () -> Unit,
    private val stopSpeaking: () -> Unit,
    /** 같은 곳에서 지난번에 고른 용도 ("네"라고 하면 그 용도) */
    private val suggestion: suspend (tripId: String) -> TripPurpose?,
    /** 확인한 용도·업무 내용으로 운행을 마친다 (확인 때 이미 되읽었으므로 분류 안내는 업무 내용을 다시 읽지 않는다) */
    private val record: suspend (TripPurpose, String?) -> VoiceRecord,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var sessionId = 0

    @Volatile
    private var disabled = false

    val enabled: Boolean get() = settings.voiceJournal

    /** 정차 안내 끝에 말로 물을지. 안내 문장도 이 값에 따라 바뀐다 */
    val asksAfterParking: Boolean get() = settings.voiceJournal && guidanceOn() && !disabled

    fun setEnabled(on: Boolean) {
        settings.voiceJournal = on
        if (!on) cancel()
    }

    /** 정차 안내를 읽기 시작한 직후 기록기가 부른다 */
    fun onParkedAnnounced() {
        if (asksAfterParking) begin(auto = true)
    }

    /**
     * 분류 화면의 [말로 기록]·[다시 말하기] 버튼.
     * 들은 내용을 확인하는 중이면 대답으로 듣는다 ("맞아"면 기록, 다른 내용이면 그 내용으로 다시 확인).
     * 확인 중에 누른 버튼을 새 문장으로 들으면 "업무 그렇죠"의 "그렇죠"가 업무 내용이 된다 (2026-11-05 사람 목소리 시험).
     * [clips]를 주면 마이크 대신 그 소리들을 차례로 인식한다 (에뮬레이터 시험용. 다 쓰면 말이 없는 것으로 본다)
     */
    fun listenNow(clips: List<ByteArray> = emptyList()) {
        disabled = false
        begin(auto = false, clips = clips, pending = _state.value as? VoiceInputState.Confirming)
    }

    /**
     * 확인 중인 내용으로 기록한다 ([맞아요] 버튼, 또는 용도 버튼으로 용도만 바꿔서).
     * 확인 중이 아니면 false (화면은 평소처럼 분류한다)
     */
    @Synchronized
    fun confirm(purpose: TripPurpose? = null): Boolean {
        val pending = _state.value as? VoiceInputState.Confirming ?: return false
        if (tripState.value !is TripState.Parked) return false
        sessionId++
        job?.cancel()
        listener.cancel()
        val chosen = purpose ?: pending.purpose
        val note = pending.workNote.takeIf { chosen != TripPurpose.PERSONAL }
        stopSpeaking()
        _state.value = VoiceInputState.Recording(chosen, note)
        job = scope.launch { save(chosen, note) }
        return true
    }

    @Synchronized
    fun cancel() {
        sessionId++
        job?.cancel()
        job = null
        listener.cancel()
        _state.value = VoiceInputState.Idle
    }

    @Synchronized
    private fun begin(auto: Boolean, clips: List<ByteArray> = emptyList(), pending: VoiceInputState.Confirming? = null) {
        val parked = tripState.value as? TripState.Parked ?: return
        job?.cancel()
        listener.cancel()
        val id = ++sessionId
        job = scope.launch {
            val session = this
            // 운전자가 화면에서 고르거나 다시 출발하면 듣기를 멈춘다
            val watcher = launch {
                tripState.first { (it as? TripState.Parked)?.tripId != parked.tripId }
                listener.cancel()
                session.cancel()
            }
            try {
                Conversation(parked.tripId, auto, clips, pending) { watcher.cancel() }.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "말로 기록 실패", e)
                _state.value = VoiceInputState.Idle
            } finally {
                watcher.cancel()
                // 멈춘 세션이 다음 세션의 상태를 지우지 않도록. 확인 화면은 버튼으로 고를 수 있게 남긴다
                val current = _state.value
                if (sessionId == id) {
                    when {
                        current is VoiceInputState.Preparing || current is VoiceInputState.Listening -> _state.value = VoiceInputState.Idle
                        current is VoiceInputState.Confirming && current.listening ->
                            _state.value = current.copy(listening = false, partial = "")
                    }
                }
            }
        }
    }

    private suspend fun save(purpose: TripPurpose, note: String?) {
        val result = record(purpose, note)
        if (result == VoiceRecord.Failed || result == VoiceRecord.NoTrip) say(VoicePhrases.PICK_ON_SCREEN)
        _state.value = VoiceInputState.Idle
    }

    /** 한 번의 말로 기록 (묻기 → 듣기 → 필요하면 업무 내용 묻기 → 맞는지 확인 → 기록) */
    private inner class Conversation(
        private val tripId: String,
        private val auto: Boolean,
        private val clips: List<ByteArray>,
        /** 확인 중에 버튼을 눌렀으면 그 내용에 대한 대답부터 듣는다 */
        private val pending: VoiceInputState.Confirming?,
        private val stopWatching: () -> Unit
    ) {
        private var heardClips = 0

        /** 다시 말해 달라고 한 횟수 (못 알아들음, 확인에서 "아니") */
        private var retries = 0

        suspend fun run() {
            val suggested = runCatching { suggestion(tripId) }.getOrNull()
            if (auto) {
                _state.value = VoiceInputState.Preparing
                awaitQuiet()
                delay(AFTER_SPEECH_MS)
            } else {
                stopSpeaking()
            }
            var candidate: SpokenTrip? = pending?.let { SpokenTrip(it.purpose, it.workNote) }
            var askFirst = pending == null
            while (true) {
                if (candidate == null) {
                    candidate = when (val hearing = hear { VoiceInputState.Listening(it) }) {
                        is Hearing.Unavailable -> return unavailable(hearing)
                        Hearing.Silence -> {
                            _state.value = if (auto && retries == 0) VoiceInputState.Idle else VoiceInputState.NoSpeech
                            return
                        }
                        is Hearing.Heard -> {
                            val spoken = SpokenTripParser.parse(hearing.text, suggested)
                            when {
                                spoken.cancelled -> {
                                    _state.value = VoiceInputState.Idle
                                    return
                                }
                                spoken.purpose == null -> {
                                    if (!askAgain(VoicePhrases.ASK_AGAIN, VoiceInputState.NotUnderstood(hearing.text))) return
                                    null
                                }
                                else -> spoken
                            }
                        }
                    } ?: continue
                }
                val purpose = candidate.purpose ?: return
                val note = candidate.workNote ?: askNote(purpose)
                val answer = confirm(purpose, note, askFirst)
                askFirst = true
                when (answer) {
                    Answer.Yes -> {
                        // 기록하면 정차 상태가 끝나므로 감시를 먼저 멈춘다
                        stopWatching()
                        _state.value = VoiceInputState.Recording(purpose, note)
                        save(purpose, note)
                        return
                    }
                    Answer.No -> {
                        if (!askAgain(VoicePhrases.SAY_AGAIN, null)) return
                        candidate = null
                    }
                    // 고쳐 말한 내용으로 다시 확인한다 (업무 내용을 빼고 말했으면 일반업무는 다시 묻는다)
                    is Answer.Correction -> candidate = answer.spoken
                    Answer.None -> return
                }
            }
        }

        /**
         * 들은 내용을 되읽고 맞는지 듣는다. 알아듣지 못하면 한 번 더 묻는다.
         * [askFirst]가 false면(확인 화면에서 버튼을 눌렀으면) 되읽지 않고 바로 대답을 듣는다
         */
        private suspend fun confirm(purpose: TripPurpose, note: String?, askFirst: Boolean): Answer {
            repeat(CONFIRM_TRIES) { attempt ->
                _state.value = VoiceInputState.Confirming(purpose, note)
                when {
                    attempt == 0 && askFirst -> prompt(VoicePhrases.confirm(purpose, note))
                    attempt > 0 -> prompt(VoicePhrases.CONFIRM_AGAIN)
                }
                val hearing = hear { VoiceInputState.Confirming(purpose, note, listening = true, partial = it) }
                if (hearing is Hearing.Unavailable) {
                    unavailable(hearing)
                    _state.value = VoiceInputState.Confirming(purpose, note)
                    return Answer.None
                }
                val text = (hearing as? Hearing.Heard)?.text
                if (text == null) {
                    // 대답이 없으면 확인 화면을 남긴다 (버튼으로 [맞아요] 또는 용도를 고를 수 있다)
                    _state.value = VoiceInputState.Confirming(purpose, note)
                    return Answer.None
                }
                when (val reply = SpokenTripParser.reply(text, purpose)) {
                    SpokenReply.Yes -> return Answer.Yes
                    SpokenReply.No -> return Answer.No
                    is SpokenReply.Correction -> return Answer.Correction(reply.spoken)
                    SpokenReply.Cancel -> {
                        _state.value = VoiceInputState.Confirming(purpose, note)
                        return Answer.None
                    }
                    SpokenReply.Unclear -> Unit
                }
            }
            _state.value = VoiceInputState.Confirming(purpose, note)
            return Answer.None
        }

        /**
         * 일반업무인데 업무 내용을 말하지 않았으면 한 번 더 묻는다.
         * 음성 엔진은 말 사이가 1초쯤 비면 끝난 것으로 보아 "업무, 거래처 미팅"이 "업무"만 오기도 한다 (2026-11-04 에뮬레이터 실측).
         * 대답이 없거나 "없어"라고 하면 업무 내용 없이 확인으로 넘어간다
         */
        private suspend fun askNote(purpose: TripPurpose): String? {
            if (purpose != TripPurpose.BUSINESS) return null
            // 되묻는 동안에도 화면에 무엇을 기다리는지 보여 준다
            _state.value = VoiceInputState.Listening("", forNote = true)
            prompt(VoicePhrases.ASK_NOTE)
            val answer = hear { VoiceInputState.Listening(it, forNote = true) } as? Hearing.Heard ?: return null
            return SpokenTripParser.noteAnswer(answer.text)
        }

        /** 다시 말해 달라고 한다. 너무 여러 번이면 화면에서 고르게 하고 false */
        private suspend fun askAgain(phrase: String, shown: VoiceInputState?): Boolean {
            retries++
            if (retries > MAX_RETRIES) {
                _state.value = shown ?: VoiceInputState.NoSpeech
                say(VoicePhrases.PICK_ON_SCREEN)
                return false
            }
            _state.value = shown ?: VoiceInputState.Listening("")
            prompt(phrase)
            return true
        }

        private fun unavailable(hearing: Hearing.Unavailable) {
            Log.i(TAG, "음성 인식을 쓸 수 없음: ${hearing.reason}")
            if (hearing.permanent) disabled = true
            _state.value = if (auto) VoiceInputState.Idle else VoiceInputState.Unavailable
        }

        private suspend fun prompt(text: String) {
            say(text)
            awaitQuiet()
            delay(AFTER_SPEECH_MS)
        }

        private suspend fun hear(shown: (String) -> VoiceInputState): Hearing {
            _state.value = shown("")
            if (clips.isNotEmpty() && heardClips >= clips.size) return Hearing.Silence
            val audio = clips.getOrNull(heardClips++)
            return listener.listen(LISTEN_MS, onPartial = { _state.value = shown(it) }, audio = audio)
        }
    }

    private sealed interface Answer {
        data object Yes : Answer
        data object No : Answer
        data class Correction(val spoken: SpokenTrip) : Answer

        /** 대답이 없거나 끝내 모름: 확인 화면을 남긴다 */
        data object None : Answer
    }

    companion object {
        private const val TAG = "DriveLog/Listen"

        /** 첫 말을 기다리는 최대 시간. 말하기 시작하면 끝날 때까지 듣는다 */
        const val LISTEN_MS = 8_000L

        /** 안내가 끝나고 듣기 시작할 때까지 (스피커 잔향을 받아 적지 않도록) */
        const val AFTER_SPEECH_MS = 300L

        /** 다시 말해 달라고 하는 최대 횟수 */
        const val MAX_RETRIES = 2

        /** 확인 대답을 알아듣지 못했을 때 묻는 횟수 */
        const val CONFIRM_TRIES = 2
    }
}
