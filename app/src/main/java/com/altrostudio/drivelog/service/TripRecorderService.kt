package com.altrostudio.drivelog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.MainActivity
import com.altrostudio.drivelog.R

/**
 * 앱 화면이 닫혀 있어도 운행을 감지하도록 기록 엔진을 살려 두는 포그라운드 서비스.
 * 개인정보 수집·이용에 동의한 경우에만 시작한다.
 */
class TripRecorderService : Service() {

    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        goForeground()
        (application as DriveLogApp).container.startRecording()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 시스템이 서비스를 되살릴 때(백그라운드)는 포그라운드로 못 올린다. 앱 화면이 다시 열려 start()를 부르면 그때 올린다
        if (!foreground) goForeground()
        return START_STICKY
    }

    /**
     * 포그라운드로 올린다. Android 12부터는 앱이 백그라운드일 때 막힌다
     * (2026-11-08 에뮬레이터 시스템이 재시작하며 START_STICKY로 서비스를 되살리자 ForegroundServiceStartNotAllowedException으로 앱이 꺼졌다).
     * 막혀도 앱을 끄지 않고 기록 엔진은 그대로 돌린다
     */
    private fun goForeground() {
        foreground = try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                // specialUse 유형은 Android 14(API 34, Pleos Connect)부터. 그 전에는 유형 없이 시작한다
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "포그라운드로 올리지 못함 (백그라운드에서 시작됨): ${e.javaClass.simpleName}")
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drivelog)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.recording_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "DriveLog/Service"
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TripRecorderService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripRecorderService::class.java))
        }
    }
}
