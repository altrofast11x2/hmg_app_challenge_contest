package com.altrostudio.drivelog.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.voice.PleosListener
import com.altrostudio.drivelog.domain.voice.SpokenTripParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 말로 기록 시험 (debug 빌드에만 있음). 에뮬레이터에서는 사람이 말할 수 없어서
 * 미리 만든 PCM(16 kHz·16비트·모노)을 마이크 대신 Pleos SpeechToText에 넣는다 (sendAudio, ON_DEVICE 전용).
 *
 *   adb exec-in run-as <패키지> --user 10 sh -c 'cat > files/voice.pcm' < business.pcm
 *   adb shell am broadcast --user 10 -n <패키지>/com.altrostudio.drivelog.debug.VoiceFeedReceiver --es mode journal --es files voice.pcm,note.pcm
 *
 * mode: journal = 정차 중인 운행에 [말로 기록]처럼 넣는다 (files의 소리를 차례로: 첫 대답, 다시 물었을 때의 대답) / engine = 엔진만 시험해 받아 적은 글자와 해석 결과를 로그로 / mic = 마이크로 5초 듣기
 * 시험용이라 받아 적은 글자를 로그(DriveLog/VoiceFeed)에 남긴다. release에는 들어가지 않는다
 */
class VoiceFeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as DriveLogApp
        val mode = intent.getStringExtra("mode") ?: "journal"
        val files = (intent.getStringExtra("files") ?: "voice.pcm").split(',').map { File(context.filesDir, it.trim()) }
        val file = files.first()
        scope.launch {
            try {
                when (mode) {
                    "journal" -> {
                        val clips = files.map { it.readBytes() }
                        Log.i(TAG, "말로 기록에 소리 ${clips.size}개를 넣음")
                        app.container.voiceJournal.listenNow(clips)
                    }
                    "engine" -> {
                        val pcm = file.readBytes()
                        val hearing = listener(context).listen(10_000L, onPartial = { Log.i(TAG, "중간: $it") }, audio = pcm)
                        Log.i(TAG, "결과: $hearing")
                        if (hearing is com.altrostudio.drivelog.domain.voice.Hearing.Heard) {
                            Log.i(TAG, "해석: ${SpokenTripParser.parse(hearing.text)}")
                        }
                    }
                    "mic" -> {
                        val hearing = listener(context).listen(5_000L, onPartial = { Log.i(TAG, "중간: $it") })
                        Log.i(TAG, "마이크 결과: $hearing")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "시험 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DriveLog/VoiceFeed"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var shared: PleosListener? = null

        fun listener(context: Context): PleosListener =
            shared ?: PleosListener(context.applicationContext).also { shared = it }
    }
}
