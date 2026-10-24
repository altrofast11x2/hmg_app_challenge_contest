package com.hmgchallenge.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val vehicle by lazy { Vehicle(applicationContext) }
    private var isInitialized = false
    private var status by mutableStateOf("Vehicle SDK 초기화 중...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isInitialized = runCatching { vehicle.initialize() }
            .onFailure { status = "초기화 실패: ${it.message}" }
            .isSuccess

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StatusScreen(status = status, onRefresh = ::readDriverDoorPosition)
                }
            }
        }
        readDriverDoorPosition()
    }

    // Pleos 문서의 첫 API 호출 예제: 운전석 도어 위치 조회 (CAR_DOORS 권한)
    private fun readDriverDoorPosition() {
        if (!isInitialized) return
        status = "도어 위치 조회 중..."
        vehicle.getDoor().getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { pos -> runOnUiThread { status = "운전석 도어 위치: $pos" } },
            onFailed = { e -> runOnUiThread { status = "조회 실패: ${e.message}" } }
        )
    }

    override fun onDestroy() {
        // 콜백 처리 이후, SDK가 더 이상 필요 없을 때 release()
        if (isInitialized) vehicle.release()
        super.onDestroy()
    }
}

@Composable
private fun StatusScreen(status: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(text = status, fontSize = 28.sp)
        Button(onClick = onRefresh) {
            Text(text = "다시 조회", fontSize = 24.sp)
        }
    }
}
