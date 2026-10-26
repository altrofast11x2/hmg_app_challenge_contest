package com.altrostudio.drivelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.altrostudio.drivelog.service.TripRecorderService
import com.altrostudio.drivelog.ui.DriveLogRoot
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 동의한 경우에만 운행 기록을 켠다 (앱을 열 때마다 서비스가 살아 있는지 확인)
        if ((application as DriveLogApp).container.consent.consentGiven.value) TripRecorderService.start(this)
        enableEdgeToEdge()
        setContent {
            DriveLogTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DriveLogRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
