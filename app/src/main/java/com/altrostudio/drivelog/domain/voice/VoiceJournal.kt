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

    /** 받아 적은 글자로 기록하는 중 */
    data class Recording(val heard: String) : VoiceInputState

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
 * 정차 안내 끝에 "용도와 업무 내용을 말씀해 주세요"라고 묻고, 안내가 끝나면 한 번 듣는다.
 * "업무, 거래처 납품 미팅"이라고 하면 일반업무로 마치고 업무 내용을 적는다. 운전자는 화면을 누르지 않아도 된다.
 * - 일반업무인데 업무 내용이 없으면 "어떤 업무였는지 말씀해 주세요"라고 한 번 더 묻는다
 * - 알아듣지 못하면 한 번만 다시 묻고, 그래도 모르면 화면에서 고르게 한다
 * - 자동으로 물었는데 말이 없으면 조용히 끝낸다 (차에서 내리는 중일 수 있다)
 * - 듣는 동안 운전자가 화면에서 고르거나 다시 출발하면 바로 멈춘다
 * - 분류 화면의 [말로 기록] 버튼으로 언제든 다시 들을 수 있다 (음성 안내를 꺼도 된다)
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
     * 분류 화면의 [말로 기록] 버튼.
     * [clips]를 주면 마이크 대신 그 소리들을 차례로 인식한다 (에뮬레이터 시험용. 다 쓰면 말이 없는 것으로 본다)
     */
    fun listenNow(clips: List<ByteArray> = emptyList()) {
        disabled = false
        begin(auto = false, clips = clips)
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
    private fun begin(auto: Boolean, clips: List<ByteArray> = emptyList()) {
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
                Conversation(parked.tripId, auto, clips) { watcher.cancel() }.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "말로 기록 실패", e)
                _state.value = VoiceInputState.Idle
            } finally {
                watcher.cancel()
                // 멈춘 세션이 다음 세션의 상태를 지우지 않도록
                val current = _state.value
                if (sessionId == id && (current is VoiceInputState.Preparing || current is VoiceInputState.Listening)) {
                    _state.value = VoiceInputState.Idle
                }
            }
        }
    }

    /** 한 번의 말로 기록 (묻기 → 듣기 → 필요하면 한 번 더 묻기 → 기록) */
    private inner class Conversation(
        private val tripId: String,
        private val auto: Boolean,
        private val clips: List<ByteArray>,
        private val stopWatching: () -> Unit
    ) {
        private var heardClips = 0

        suspend fun run() {
            val suggested = runCatching { suggestion(tripId) }.getOrNull()
            if (auto) {
                _state.value = VoiceInputState.Preparing
                awaitQuiet()
                delay(AFTER_SPEECH_MS)
            } else {
                stopSpeaking()
            }
            var askedAgain = false
            while (true) {
                val hearing = hear(forNote = false)
                when (hearing) {
                    is Hearing.Unavailable -> return unavailable(hearing)
                    Hearing.Silence -> {
                        _state.value = if (auto) VoiceInputState.Idle else VoiceInputState.NoSpeech
                        return
                    }
                    is Hearing.Heard -> {
                        val spoken = SpokenTripParser.parse(hearing.text, suggested)
                        val purpose = spoken.purpose
                        when {
                            spoken.cancelled -> {
                                _state.value = VoiceInputState.Idle
                                return
                            }
                            purpose != null -> return finish(purpose, spoken.workNote ?: askNote(purpose), hearing.text)
                            !askedAgain -> {
                                askedAgain = true
                                _state.value = VoiceInputState.NotUnderstood(hearing.text)
                                prompt(VoicePhrases.ASK_AGAIN)
                            }
                            else -> {
                                _state.value = VoiceInputState.NotUnderstood(hearing.text)
                                say(VoicePhrases.PICK_ON_SCREEN)
                                return
                            }
                        }
                    }
                }
            }
        }

        /**
         * 일반업무인데 업무 내용을 말하지 않았으면 한 번 더 묻는다.
         * 음성 엔진은 말 사이가 1초쯤 비면 끝난 것으로 보아 "업무, 거래처 미팅"이 "업무"만 오기도 한다 (2026-11-04 에뮬레이터 실측).
         * 대답이 없거나 "없어"라고 하면 업무 내용 없이 기록한다
         */
        private suspend fun askNote(purpose: TripPurpose): String? {
            if (purpose != TripPurpose.BUSINESS) return null
            // 되묻는 동안에도 화면에 무엇을 기다리는지 보여 준다
            _state.value = VoiceInputState.Listening("", forNote = true)
            prompt(VoicePhrases.ASK_NOTE)
            val answer = hear(forNote = true) as? Hearing.Heard ?: return null
            return SpokenTripParser.noteAnswer(answer.text)
        }

        private suspend fun finish(purpose: TripPurpose, note: String?, heard: String) {
            // 기록하면 정차 상태가 끝나므로 감시를 먼저 멈춘다
            stopWatching()
            _state.value = VoiceInputState.Recording(heard)
            val result = record(purpose, note)
            if (result == VoiceRecord.Failed || result == VoiceRecord.NoTrip) say(VoicePhrases.PICK_ON_SCREEN)
            _state.value = VoiceInputState.Idle
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

        private suspend fun hear(forNote: Boolean): Hearing {
            _state.value = VoiceInputState.Listening("", forNote)
            if (clips.isNotEmpty() && heardClips >= clips.size) return Hearing.Silence
            val audio = clips.getOrNull(heardClips++)
            return listener.listen(LISTEN_MS, onPartial = { _state.value = VoiceInputState.Listening(it, forNote) }, audio = audio)
        }
    }

    companion object {
        private const val TAG = "DriveLog/Listen"

        /** 한 번에 듣는 최대 시간. 말이 끝나면 음성 엔진이 먼저 끝낸다 */
        const val LISTEN_MS = 8_000L

        /** 안내가 끝나고 듣기 시작할 때까지 (스피커 잔향을 받아 적지 않도록) */
        const val AFTER_SPEECH_MS = 300L
    }
}
