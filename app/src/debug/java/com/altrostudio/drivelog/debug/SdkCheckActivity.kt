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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.ui.theme.DriveLogTheme

/** debug 빌드 전용: Pleos SDK·AAOS Car API 연결과 운행 기록 엔진 확인 화면 */
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
                        actions = SdkCheckActions(
                            readVin = viewModel::readVin,
                            readEfficiency = viewModel::readEfficiency,
                            readBattery = viewModel::readBattery,
                            readCurrentPlace = viewModel::readCurrentPlace,
                            readDestination = viewModel::readDestination,
                            startRecording = viewModel::startRecording,
                            toggleDemoTimeout = viewModel::toggleDemoTimeout,
                            classify = viewModel::classify,
                            verifyChain = viewModel::verifyChain
                        ),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private class SdkCheckActions(
    val readVin: () -> Unit,
    val readEfficiency: () -> Unit,
    val readBattery: () -> Unit,
    val readCurrentPlace: () -> Unit,
    val readDestination: () -> Unit,
    val startRecording: () -> Unit,
    val toggleDemoTimeout: () -> Unit,
    val classify: (TripPurpose) -> Unit,
    val verifyChain: () -> Unit
)

@Composable
private fun SdkCheckScreen(state: SdkCheckState, actions: SdkCheckActions, modifier: Modifier = Modifier) {
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
            style = DriveLogTheme.typography.title,
            color = DriveLogTheme.colors.buttonPrimaryText,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (state.drivingRestricted) DriveLogTheme.colors.statusError else DriveLogTheme.colors.buttonPrimaryBackground)
                .padding(16.dp)
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Section(title = "Vehicle SDK · AAOS Car API", modifier = Modifier.weight(1f)) {
                ValueRow("계기판 누적 거리", state.odometer)
                ValueRow("기어 (AAOS GEAR_SELECTION)", state.gear)
                ValueRow("주차 브레이크 (AAOS PARKING_BRAKE_ON)", state.parkingBrake)
                ValueRow("VIN", state.vin, actions.readVin, enabled)
                ValueRow("연비 / 전비", state.efficiency, actions.readEfficiency, enabled)
                ValueRow("배터리 잔량", state.battery, actions.readBattery, enabled)
                ValueRow("현재 위치 (NaviHelper)", state.currentPlace, actions.readCurrentPlace, enabled)
                ValueRow("목적지 (NaviHelper)", state.destination, actions.readDestination, enabled)
            }
            Section(title = "운행 기록 엔진", modifier = Modifier.weight(1f)) {
                ValueRow("상태", state.recorderState)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = actions.startRecording,
                        enabled = enabled && !state.consentGiven,
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) {
                        Text(if (state.consentGiven) "기록 켜짐" else "기록 시작 (디버그: 동의 처리)")
                    }
                    Button(onClick = actions.toggleDemoTimeout, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("정차 대기 ${state.parkedTimeout}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PURPOSE_LABELS.forEach { (purpose, label) ->
                        Button(
                            onClick = { actions.classify(purpose) },
                            enabled = enabled && state.canClassify,
                            modifier = Modifier.heightIn(min = 56.dp)
                        ) {
                            Text(label)
                        }
                    }
                }
                ValueRow("해시 체인", state.chain, actions.verifyChain, enabled)
                Text(text = "최근 운행 (최신 순)", style = DriveLogTheme.typography.labelSmall)
                state.trips.forEach { line -> Text(text = line, style = DriveLogTheme.typography.label) }
            }
        }
        Text(text = "이벤트 기록 (최근 순)", style = DriveLogTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.weight(0.5f)) {
            items(state.log) { line -> Text(text = line, style = DriveLogTheme.typography.label) }
        }
    }
}

private val PURPOSE_LABELS = listOf(
    TripPurpose.COMMUTE to "출퇴근",
    TripPurpose.BUSINESS to "일반업무",
    TripPurpose.PERSONAL to "비업무"
)

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = DriveLogTheme.typography.title)
        content()
    }
}

@Composable
private fun ValueRow(label: String, value: String, onRefresh: (() -> Unit)? = null, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = DriveLogTheme.typography.labelSmall)
            Text(text = value, style = DriveLogTheme.typography.titleSmall)
        }
        if (onRefresh != null) {
            Button(onClick = onRefresh, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("다시 조회")
            }
        }
    }
}
