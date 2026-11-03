package com.altrostudio.drivelog

import android.app.Application

class DriveLogApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 조직에 연결돼 있으면 올릴 기록이 생길 때마다 동기화한다
        container.startBackground()
    }
}
