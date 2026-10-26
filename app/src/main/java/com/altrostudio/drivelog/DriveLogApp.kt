package com.altrostudio.drivelog

import android.app.Application

class DriveLogApp : Application() {
    val container by lazy { AppContainer(this) }
}
