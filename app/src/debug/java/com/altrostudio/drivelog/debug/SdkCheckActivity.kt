package com.altrostudio.drivelog.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/** debug 빌드 전용: Pleos SDK와 AAOS Car API 연결 확인 화면 */
class SdkCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveLogTheme {
                val viewModel: SdkCheckViewModel = viewModel(factory = SdkCheckViewModel.Factory)
                val state by viewModel.state.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SdkCheckScreen(
                        state = state,
                        onReadVin = viewModel::readVin,
                        onReadEfficiency = viewModel::readEfficiency,
                        onProbeEnergy = viewModel::probeEnergy,
                        onReadCurrentPlace = viewModel::readCurrentPlace,
                        onReadDestination = viewModel::readDestination,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun SdkCheckScreen(
    state: SdkCheckState,
    onReadVin: () -> Unit,
    onReadEfficiency: () -> Unit,
    onProbeEnergy: () -> Unit,
    onReadCurrentPlace: () -> Unit,
    onReadDestination: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 주행 중에는 버튼을 잠근다 (조작은 정차 중에만)
    val enabled = !state.drivingRestricted
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (state.drivingRestricted) "주행 중: 조작 잠김" else "정차 중: 조작 가능",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (state.drivingRestricted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .padding(16.dp)
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Section(title = "Vehicle SDK · AAOS Car API", modifier = Modifier.weight(1f)) {
                ValueRow("계기판 누적 거리", state.odometer)
                ValueRow("기어 (AAOS GEAR_SELECTION)", state.gear)
                ValueRow("주차 브레이크 (AAOS PARKING_BRAKE_ON)", state.parkingBrake)
                ValueRow("VIN", state.vin, onReadVin, enabled)
                ValueRow("연비 / 전비", state.efficiency, onReadEfficiency, enabled)
                ValueRow("에너지 값 실험 (EV · 연료)", state.energyProbe, onProbeEnergy, enabled)
            }
            Section(title = "NaviHelper", modifier = Modifier.weight(1f)) {
                ValueRow("현재 위치", state.currentPlace, onReadCurrentPlace, enabled)
                ValueRow("목적지", state.destination, onReadDestination, enabled)
            }
        }
        Text(text = "이벤트 기록 (최근 순)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(0.6f)) {
            items(state.log) { line -> Text(text = line, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ValueRow(label: String, value: String, onRefresh: (() -> Unit)? = null, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
        if (onRefresh != null) {
            Button(onClick = onRefresh, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("다시 조회")
            }
        }
    }
}
