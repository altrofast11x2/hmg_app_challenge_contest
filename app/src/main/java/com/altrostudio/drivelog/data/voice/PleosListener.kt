package com.altrostudio.drivelog.data.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.altrostudio.drivelog.domain.voice.Hearing
import com.altrostudio.drivelog.domain.voice.Listener
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Pleos SpeechToText SDK로 차량 마이크의 말을 받아 적는다 (docs/sdk-usage.md).
 * 차량 안 음성 엔진(ON_DEVICE)만 쓴다: 서버 모드에 필요한 Client Secret을 APK에 넣지 않고, 목소리도 차 밖으로 나가지 않는다.
 * 앱은 녹음을 받지 않고 받아 적은 글자만 받는다. 받아 적은 글자는 로그에 남기지 않는다 (Pleos 앱 보안 가이드).
 *
 * 듣는 동안은 음성 인식 용도로 잠깐 혼자 쓰는 오디오 포커스(GAIN_TRANSIENT_EXCLUSIVE)를 받아 음악을 멈춘다.
 * 받지 못하면(통화 중 등) 듣지 않는다. SDK 호출과 상태는 메인 스레드에서만 다룬다.
 *
 * 이어 듣기: 차 안 엔진은 말 사이가 조금만 비어도 끝난 것으로 본다 (2026-11-04 사람 목소리 실측: "업무 거래처 | 납품 미팅"이
 * "업무 거래처"에서 끊김). 공개 SDK에는 끝 판정 시간을 바꾸는 방법이 없어서(AmbientAi API는 시스템 앱 전용),
 * 한 구간이 끝나면 바로 다시 듣고 [CONTINUE_WINDOW_MS] 안에 말이 이어지면 붙인다 (1.5초는 끝나고 기다리는 게 길다는 사용자 의견으로 1초). 이어지지 않으면 들은 데까지로 끝낸다
 */
class PleosListener(private val context: Context) : Listener {

    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener(::onFocusChange, main)
        .build()

    private var engine: SpeechToText? = null
    private var connecting: CompletableDeferred<SpeechToText?>? = null
    private var request: Request? = null
    private var lastInitError: String? = null
    private var initErrorPermanent = false

    private class Request(
        val continuation: CancellableContinuation<Hearing>,
        val onPartial: (String) -> Unit,
        /** 마이크로 들을 때만 이어 듣는다 (시험용 소리 넣기는 한 번에 끝) */
        val continuous: Boolean
    ) {
        /** 끝난 구간들 */
        var committed = ""

        /** 지금 구간에서 받아 적는 중인 글자 */
        var text = ""
        var segments = 1
        var timer: Runnable? = null

        val full: String get() = listOf(committed, text).filter { it.isNotBlank() }.joinToString(" ")
    }

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) = onMain {
            val current = request ?: return@onMain
            current.text = stt
            current.onPartial(current.full)
            if (completed) segmentDone(current)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        // 끝 신호는 쓰지 않는다: 이어 듣기에서 앞 구간의 끝 신호가 다음 구간을 연 뒤에 올 수 있다.
        // 말이 있으면 완료 결과(completed)가 오고, 말이 없으면 엔진이 끝내지 않아 앱의 시간 제한으로 끝난다 (에뮬레이터 실측)
        override fun onEndedRecognition() = Unit

        override fun onError(errorType: ErrorType) = onMain {
            Log.i(TAG, "음성 인식 오류: $errorType")
            val current = request ?: return@onMain
            finish(current, hearingFor(errorType, current.full))
        }
    }

    override suspend fun listen(maxMs: Long, onPartial: (String) -> Unit, audio: ByteArray?): Hearing =
        withContext(Dispatchers.Main.immediate) {
            val stt = connect() ?: return@withContext Hearing.Unavailable(lastInitError ?: "init", initErrorPermanent)
            if (audio == null && audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return@withContext Hearing.Unavailable("audio focus", permanent = false)
            }
            var started: Request? = null
            try {
                // 말하는 중에 끊지 않도록 전체 상한은 넉넉히 두고, 첫 말을 기다리는 시간([maxMs])은 따로 잰다
                withTimeoutOrNull(maxMs + MAX_UTTERANCE_EXTRA_MS) {
                    suspendCancellableCoroutine { continuation ->
                        val current = Request(continuation, onPartial, continuous = audio == null)
                        started = current
                        request = current
                        try {
                            if (audio == null) {
                                stt.request()
                            } else {
                                stt.sendAudio(audio)
                                stt.completeAudioSend()
                            }
                            waitForSpeech(current, maxMs)
                        } catch (e: Exception) {
                            Log.w(TAG, "음성 인식 요청 실패: ${e.message}")
                            finish(current, Hearing.Unavailable("request", permanent = false))
                        }
                    }
                } ?: heard(started?.full.orEmpty())
            } finally {
                started?.timer?.let(main::removeCallbacks)
                if (request === started) request = null
                runCatching { stt.stop() }
                if (audio == null) audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }

    /** [ms] 안에 이번 구간의 말이 시작되지 않으면 들은 데까지로 끝낸다. 말하는 중이면 완료 결과를 기다린다 */
    private fun waitForSpeech(current: Request, ms: Long) {
        current.timer?.let(main::removeCallbacks)
        val check = Runnable { if (request === current && current.text.isBlank()) finish(current, heard(current.committed)) }
        current.timer = check
        main.postDelayed(check, ms)
    }

    /** 한 구간이 끝났다. 마이크로 듣는 중이면 바로 다시 들어 이어지는 말을 기다린다 */
    private fun segmentDone(current: Request) {
        current.committed = current.full
        current.text = ""
        val stt = engine
        if (!current.continuous || stt == null || current.segments >= MAX_SEGMENTS) {
            finish(current, heard(current.committed))
            return
        }
        current.segments++
        try {
            stt.stop()
            stt.request()
            waitForSpeech(current, CONTINUE_WINDOW_MS)
        } catch (e: Exception) {
            Log.w(TAG, "이어 듣기 실패: ${e.message}")
            finish(current, heard(current.committed))
        }
    }

    /**
     * 앱이 켜질 때 한 번: 지난 실행에서 듣던 중 앱이 죽었으면 음성 서비스가 그 듣기를 이어 가며 마이크를 계속 연다
     * (2026-11-05 실측: 듣는 중 앱을 다시 설치했더니 서비스가 73초 동안 녹음하다 스스로 끝냄). 그래서 연결하자마자 멈춘다
     */
    suspend fun stopLeftover() = withContext(Dispatchers.Main.immediate) {
        if (request != null) return@withContext
        val stt = connect() ?: return@withContext
        runCatching { stt.stop() }
        Unit
    }

    override fun cancel() = onMain {
        val current = request ?: return@onMain
        finish(current, Hearing.Silence)
        runCatching { engine?.stop() }
    }

    private suspend fun connect(): SpeechToText? {
        engine?.let { return it }
        connecting?.let { return it.await() }
        val waiting = CompletableDeferred<SpeechToText?>()
        connecting = waiting
        val connected = createAndInitialize()
        engine = connected
        connecting = null
        waiting.complete(connected)
        return connected
    }

    private suspend fun createAndInitialize(): SpeechToText? {
        val created = try {
            SpeechToText(context, Mode.ON_DEVICE)
        } catch (e: Exception) {
            // 권한이 없거나 음성 서비스가 없는 기기 (테스트용 일반 에뮬레이터)
            Log.w(TAG, "음성 인식을 쓸 수 없음: ${e.message}")
            lastInitError = "create"
            initErrorPermanent = true
            return null
        }
        val initialized = CompletableDeferred<ErrorType?>()
        try {
            created.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    initialized.complete(null)
                }
                override fun onFailure(errorType: ErrorType) {
                    initialized.complete(errorType)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "음성 인식 초기화 실패: ${e.message}")
            initialized.complete(ErrorType.UNKNOWN)
        }
        val outcome = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { initialized.await() ?: Connected }
        if (outcome !== Connected) {
            val error = outcome as? ErrorType
            Log.w(TAG, "음성 인식 연결 실패: ${error ?: "TIMEOUT"}")
            lastInitError = error?.name ?: "TIMEOUT"
            initErrorPermanent = error in PERMANENT_ERRORS
            runCatching { created.release() }
            return null
        }
        return try {
            created.addListener(results)
            created.setLocale(Locale.KOREA)
            created
        } catch (e: Exception) {
            Log.w(TAG, "음성 인식 설정 실패: ${e.message}")
            lastInitError = "setup"
            initErrorPermanent = false
            runCatching { created.release() }
            null
        }
    }

    private fun finish(current: Request, hearing: Hearing) {
        current.timer?.let(main::removeCallbacks)
        if (request === current) request = null
        if (current.continuation.isActive) current.continuation.resume(hearing)
    }

    /** 오류 중에도 받아 적은 글자가 있으면 그것을 쓴다. 엔진 오류면 다음에 처음부터 다시 연결한다 */
    private fun hearingFor(error: ErrorType, text: String): Hearing {
        if (error in PERMANENT_ERRORS) return Hearing.Unavailable(error.name, permanent = true)
        if (error != ErrorType.TIMEOUT) resetEngine()
        return when {
            text.isNotBlank() -> Hearing.Heard(text)
            error == ErrorType.TIMEOUT -> Hearing.Silence
            else -> Hearing.Unavailable(error.name, permanent = false)
        }
    }

    private fun resetEngine() {
        val old = engine ?: return
        engine = null
        runCatching { old.removeListener(results) }
        runCatching { old.release() }
    }

    private fun heard(text: String): Hearing = if (text.isBlank()) Hearing.Silence else Hearing.Heard(text.trim())

    private fun onFocusChange(change: Int) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            val current = request ?: return
            finish(current, Hearing.Unavailable("audio focus lost", permanent = false))
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private object Connected

    private companion object {
        const val TAG = "DriveLog/Listen"
        const val CONNECT_TIMEOUT_MS = 10_000L

        /** 한 구간이 끝난 뒤 말이 이어지기를 기다리는 시간 */
        const val CONTINUE_WINDOW_MS = 1_000L

        /** 첫 말을 기다리는 시간 뒤에 말하는 중이면 더 들을 수 있는 시간 */
        const val MAX_UTTERANCE_EXTRA_MS = 12_000L

        /** 한 번에 이어 붙이는 구간 수 */
        const val MAX_SEGMENTS = 4
        val PERMANENT_ERRORS = setOf(ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API, ErrorType.UNAVAILABLE_LANGUAGE)
    }
}
