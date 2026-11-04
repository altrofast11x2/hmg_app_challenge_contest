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
 * 받지 못하면(통화 중 등) 듣지 않는다. SDK 호출과 상태는 메인 스레드에서만 다룬다
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

    private class Request(val continuation: CancellableContinuation<Hearing>, val onPartial: (String) -> Unit) {
        var text = ""
    }

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) = onMain {
            val current = request ?: return@onMain
            current.text = stt
            current.onPartial(stt)
            if (completed) finish(current, heard(stt))
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = onMain {
            val current = request ?: return@onMain
            // 마지막 결과가 끝 신호 바로 뒤에 올 수 있어 잠깐 기다린다
            main.postDelayed({ if (request === current) finish(current, heard(current.text)) }, FINAL_RESULT_GRACE_MS)
        }
        override fun onError(errorType: ErrorType) = onMain {
            Log.i(TAG, "음성 인식 오류: $errorType")
            val current = request ?: return@onMain
            finish(current, hearingFor(errorType, current.text))
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
                withTimeoutOrNull(maxMs) {
                    suspendCancellableCoroutine { continuation ->
                        val current = Request(continuation, onPartial)
                        started = current
                        request = current
                        try {
                            if (audio == null) {
                                stt.request()
                            } else {
                                stt.sendAudio(audio)
                                stt.completeAudioSend()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "음성 인식 요청 실패: ${e.message}")
                            finish(current, Hearing.Unavailable("request", permanent = false))
                        }
                    }
                } ?: heard(started?.text.orEmpty())
            } finally {
                if (request === started) request = null
                runCatching { stt.stop() }
                if (audio == null) audioManager.abandonAudioFocusRequest(focusRequest)
            }
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
        const val FINAL_RESULT_GRACE_MS = 400L
        val PERMANENT_ERRORS = setOf(ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API, ErrorType.UNAVAILABLE_LANGUAGE)
    }
}
