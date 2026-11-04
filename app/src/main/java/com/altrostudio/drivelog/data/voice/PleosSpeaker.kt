package com.altrostudio.drivelog.data.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.altrostudio.drivelog.domain.voice.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos TextToSpeech SDK로 차량 스피커에서 읽어 준다 (docs/sdk-usage.md).
 * 차량 안 음성 엔진(ON_DEVICE)만 쓴다: 서버 모드에 필요한 Client Secret을 APK에 넣지 않고,
 * 읽는 문장(거리·용도)도 차 밖으로 나가지 않는다.
 * 처음 읽을 때 SDK를 연결하고, 연결되는 동안 들어온 안내는 가장 최근 것 하나만 읽는다.
 *
 * Pleos 오디오 포커스 정책: 읽기 전에 알림 용도로 "잠깐, 다른 소리는 줄여도 됨"(MAY_DUCK) 포커스를 요청하고,
 * 받지 못하면(통화·긴급 안내 중 등) 읽지 않는다. 다 읽으면 반납하고, 포커스를 잃으면 멈춘다.
 * 첫 음절이 잘리지 않도록 150 ms 뒤에 읽기 시작한다 (Pleos TTS 선행 묵음 가이드. 음성 데이터는 음성 서비스가
 * 만들어 앞에 묵음을 넣을 수 없으므로 재생을 늦추는 방법을 쓴다).
 * SDK 호출과 상태는 메인 스레드에서만 다룬다.
 * [busy]는 읽을 안내가 남아 있는 동안 true다. 말로 기록([com.altrostudio.drivelog.domain.voice.VoiceJournal])은
 * 안내가 끝난 뒤에 듣는다 (안내 소리를 받아 적지 않도록)
 */
class PleosSpeaker(private val context: Context) : Speaker {

    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(::onFocusChange, main)
        .build()

    private var tts: TextToSpeech? = null
    private var state = State.IDLE
    private var pending: String? = null

    private val _busy = MutableStateFlow(false)

    /** 읽고 있거나 읽으려고 기다리는 안내가 있다 */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // 읽기 상태: 시작을 미뤄 둔 안내, speak()는 불렀지만 아직 시작 신호가 없음, 읽는 중, 포커스를 가짐
    private var scheduled: String? = null
    private var awaitingStart = false
    private var speaking = false
    private var holdingFocus = false

    private val speakScheduled = Runnable {
        val text = scheduled ?: return@Runnable
        scheduled = null
        awaitingStart = true
        call("speak") { it.speak(text, true) }
    }
    private val focusWatchdog = Runnable {
        // 음성 서비스가 시작·끝 신호를 주지 않았다. 더 기다리지 않는다
        awaitingStart = false
        releaseFocus()
    }

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = onMain {
            awaitingStart = false
            speaking = true
        }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = onMain { finished() }
        override fun onStop() = onMain { finished() }
        override fun onError(errorType: ErrorType) = onMain {
            Log.w(TAG, "읽기 실패: $errorType")
            awaitingStart = false
            finished()
        }
    }

    override fun speak(text: String) {
        // 부른 즉시 바쁨으로 둔다 (메인 스레드로 넘어가기 전에 awaitQuiet가 끝나 버리지 않도록)
        _busy.value = true
        onMain { speakOnMain(text) }
    }

    /** 안내가 모두 끝날 때까지 기다린다. 음성 서비스가 끝 신호를 주지 않아도 [MAX_SPEECH_MS] 안에 돌아온다 */
    suspend fun awaitQuiet() {
        withTimeoutOrNull(CONNECT_TIMEOUT_MS + MAX_SPEECH_MS) { busy.first { !it } }
    }

    private fun speakOnMain(text: String) {
        when (state) {
            State.READY -> speakNow(text)
            State.CONNECTING -> pending = text
            State.IDLE -> {
                pending = text
                connect()
            }
        }
    }

    override fun stop() = onMain {
        pending = null
        scheduled = null
        awaitingStart = false
        main.removeCallbacks(speakScheduled)
        if (state == State.READY && speaking) call("stop") { it.stop() }
        releaseFocus()
    }

    private fun connect() {
        val created = try {
            TextToSpeech(context, Mode.ON_DEVICE)
        } catch (e: Exception) {
            // 권한이 없거나 음성 서비스가 없는 기기 (테스트용 일반 에뮬레이터)
            Log.w(TAG, "음성 안내를 쓸 수 없음: ${e.message}")
            pending = null
            _busy.value = false
            return
        }
        tts = created
        state = State.CONNECTING
        call("initialize") {
            it.initialize(object : OnInitializeListener {
                override fun onSuccess() = onMain { onConnected(created) }
                override fun onFailure(errorType: ErrorType) = onMain { onConnectFailed(created, errorType) }
            })
        }
        // 음성 서비스가 답하지 않으면 기다리지 않고 다음 안내 때 다시 연결한다
        main.postDelayed({ if (state == State.CONNECTING) onConnectFailed(created, ErrorType.TIMEOUT) }, CONNECT_TIMEOUT_MS)
    }

    private fun onConnected(connected: TextToSpeech) {
        if (tts !== connected) return
        state = State.READY
        call("addEventListener") { it.addEventListener(events) }
        call("setLocale") { it.setLocale(Locale.KOREA) }
        // 음성 서비스가 알림 용도로 재생하도록 (기본값은 미디어 용도라 음악처럼 취급된다)
        call("setAudioAttributes") { it.setAudioAttributes(attributes) }
        pending?.let(::speakNow)
        pending = null
    }

    /** 다음 안내 때 처음부터 다시 연결한다 */
    private fun onConnectFailed(failed: TextToSpeech, errorType: ErrorType) {
        if (tts !== failed) return
        Log.w(TAG, "음성 안내 연결 실패: $errorType")
        runCatching { failed.release() }
        tts = null
        state = State.IDLE
        pending = null
        _busy.value = false
    }

    /** 읽는 중이면 멈추고 새 안내를 읽는다 (정차 안내 중에 바로 분류해도 결과를 들려준다) */
    private fun speakNow(text: String) {
        if (!holdingFocus) {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "오디오 포커스를 받지 못해 안내를 읽지 않음")
                _busy.value = false
                return
            }
            holdingFocus = true
        }
        if (speaking) call("stop") { it.stop() }
        scheduled = text
        main.removeCallbacks(speakScheduled)
        main.postDelayed(speakScheduled, PREROLL_MS)
        // 음성 서비스가 끝 신호를 주지 않아도 다른 앱의 소리가 계속 줄어 있지 않도록
        main.removeCallbacks(focusWatchdog)
        main.postDelayed(focusWatchdog, MAX_SPEECH_MS)
    }

    /** 읽기가 끝나거나 멈췄다. 이어서 읽을 안내가 없으면 포커스를 반납한다 */
    private fun finished() {
        speaking = false
        if (scheduled == null && !awaitingStart) releaseFocus()
    }

    private fun onFocusChange(change: Int) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }

    private fun releaseFocus() {
        main.removeCallbacks(focusWatchdog)
        if (scheduled == null && !awaitingStart && pending == null) _busy.value = false
        if (!holdingFocus) return
        holdingFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun call(name: String, block: (TextToSpeech) -> Unit) {
        val current = tts ?: return
        try {
            block(current)
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech.$name 실패: ${e.message}")
            if (state == State.CONNECTING) onConnectFailed(current, ErrorType.UNKNOWN)
            if (name == "speak") {
                awaitingStart = false
                finished()
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private enum class State { IDLE, CONNECTING, READY }

    private companion object {
        const val TAG = "DriveLog/Voice"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val PREROLL_MS = 150L
        const val MAX_SPEECH_MS = 30_000L
    }
}
