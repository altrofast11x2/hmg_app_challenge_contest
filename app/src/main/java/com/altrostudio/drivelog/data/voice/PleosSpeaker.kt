package com.altrostudio.drivelog.data.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.altrostudio.drivelog.domain.voice.Speaker
import java.util.Locale

/**
 * Pleos TextToSpeech SDK로 차량 스피커에서 읽어 준다 (docs/sdk-usage.md).
 * 차량 안 음성 엔진(ON_DEVICE)만 쓴다: 서버 모드에 필요한 Client Secret을 APK에 넣지 않고,
 * 읽는 문장(거리·용도)도 차 밖으로 나가지 않는다.
 * 처음 읽을 때 SDK를 연결하고, 연결되는 동안 들어온 안내는 가장 최근 것 하나만 읽는다.
 * SDK 호출과 상태는 메인 스레드에서만 다룬다
 */
class PleosSpeaker(private val context: Context) : Speaker {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var state = State.IDLE
    private var speaking = false
    private var pending: String? = null

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = onMain { speaking = true }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = onMain { speaking = false }
        override fun onStop() = onMain { speaking = false }
        override fun onError(errorType: ErrorType) = onMain {
            speaking = false
            Log.w(TAG, "읽기 실패: $errorType")
        }
    }

    override fun speak(text: String) = onMain {
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
        if (state == State.READY && speaking) call("stop") { it.stop() }
    }

    private fun connect() {
        val created = try {
            TextToSpeech(context, Mode.ON_DEVICE)
        } catch (e: Exception) {
            // 권한이 없거나 음성 서비스가 없는 기기 (테스트용 일반 에뮬레이터)
            Log.w(TAG, "음성 안내를 쓸 수 없음: ${e.message}")
            pending = null
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
    }

    /** 읽는 중이면 멈추고 새 안내를 읽는다 (정차 안내 중에 바로 분류해도 결과를 들려준다) */
    private fun speakNow(text: String) {
        if (speaking) call("stop") { it.stop() }
        call("speak") { it.speak(text, true) }
    }

    private fun call(name: String, block: (TextToSpeech) -> Unit) {
        val current = tts ?: return
        try {
            block(current)
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech.$name 실패: ${e.message}")
            if (state == State.CONNECTING) onConnectFailed(current, ErrorType.UNKNOWN)
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private enum class State { IDLE, CONNECTING, READY }

    private companion object {
        const val TAG = "DriveLog/Voice"
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
