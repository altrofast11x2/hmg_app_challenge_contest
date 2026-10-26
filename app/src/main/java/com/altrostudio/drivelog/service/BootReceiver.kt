package com.altrostudio.drivelog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.altrostudio.drivelog.DriveLogApp

/** 차량이 켜지면(부팅) 동의한 경우에만 운행 기록을 다시 시작한다 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as DriveLogApp).container
        if (container.consent.consentGiven.value) TripRecorderService.start(context)
    }
}
