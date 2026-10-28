package com.altrostudio.drivelog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.altrostudio.drivelog.DriveLogApp

/**
 * 차량이 켜졌을 때(부팅)와 앱을 업데이트한 직후, 동의한 경우에만 운행 기록을 다시 시작한다.
 * 업데이트는 실행 중인 앱을 끄므로, 이게 없으면 운전자가 앱을 한 번 열 때까지 운행이 기록되지 않는다
 * (Pleos App Market은 사용자가 직접 업데이트한다)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as DriveLogApp).container
        if (container.consent.consentGiven.value) TripRecorderService.start(context)
    }
}
