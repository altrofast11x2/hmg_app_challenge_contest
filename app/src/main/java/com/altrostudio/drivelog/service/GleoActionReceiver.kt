package com.altrostudio.drivelog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.domain.voice.GleoActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gleo AI가 음성 명령으로 부르는 함수 (assets/schema.json, component "service" = sendBroadcast).
 * Gleo AI가 이 앱 패키지를 지정해 보내므로 외부에 노출한다 (Pleos 앱 연동 가이드).
 * 받는 일은 운행 용도·업무 내용 기록, 운전자 바꾸기, 기록부 요약뿐이고, 기록은 모두 수정 이력(바꾼 사람 "voice:")으로 남는다
 */
class GleoActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in GleoActions.ACTIONS) return
        val container = (context.applicationContext as DriveLogApp).container
        val params = intent.getStringExtra(PARAM_EXTRA)
        val pending = goAsync()
        scope.launch {
            try {
                val gleo = container.gleoActions
                val response = gleo.handle(action, params)
                // 업무 내용·이름은 로그에 남기지 않는다 (Pleos 앱 보안 가이드)
                Log.i(TAG, "Gleo $action → ${response.status.wire} (${response.mode.wire})")
                gleo.respond(response)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DriveLog/Gleo"
        const val PARAM_EXTRA = "ACTION_PARAM"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
