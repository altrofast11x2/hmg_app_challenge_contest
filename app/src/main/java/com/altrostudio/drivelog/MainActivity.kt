package com.altrostudio.drivelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.altrostudio.drivelog.service.TripRecorderService
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 동의 화면은 5단계에서 만든다. 동의한 경우에만 운행 기록을 켠다
        if ((application as DriveLogApp).container.preferences.consentGiven) TripRecorderService.start(this)
        enableEdgeToEdge()
        setContent {
            DriveLogTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WelcomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium
        )
        Text(
            text = stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
fun WelcomeScreenPreview() {
    DriveLogTheme {
        WelcomeScreen()
    }
}
