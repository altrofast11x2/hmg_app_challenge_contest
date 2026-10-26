package com.altrostudio.drivelog.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.settings.AppPreferences
import com.altrostudio.drivelog.data.sync.PairingRepository
import com.altrostudio.drivelog.data.sync.PairingResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectError { INVALID, EXPIRED, OFFLINE, TOO_MANY_ATTEMPTS, VEHICLE_IN_OTHER_ORG, NOT_CONFIGURED, UNKNOWN }

sealed interface ConnectStatus {
    data object Idle : ConnectStatus
    data object Verifying : ConnectStatus
    data class Error(val error: ConnectError) : ConnectStatus
    data class Connected(val orgName: String) : ConnectStatus
}

data class ConnectUiState(
    val code: String = "",
    val status: ConnectStatus = ConnectStatus.Idle,
    /** 확인이 2초를 넘으면 "확인하고 있어요"를 보인다 */
    val slowVerifying: Boolean = false,
    val vinConfirmed: Boolean = false
)

/**
 * 6자리 연결 코드 입력 (design/specs/A02-connect.md). 6칸이 차면 따로 버튼 없이 바로 확인한다.
 * 주행이 시작되면 입력하던 숫자를 지운다 (이미 확인 중이면 결과는 받아 둔다).
 */
class ConnectViewModel(
    private val pairing: PairingRepository,
    private val preferences: AppPreferences,
    private val vehicleId: suspend () -> String,
    drivingRestricted: Flow<Boolean>
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState(vinConfirmed = preferences.vin != null))
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vehicleId()
            _state.update { it.copy(vinConfirmed = preferences.vin != null) }
        }
        drivingRestricted.filter { it }.onEach {
            _state.update { current ->
                if (current.status is ConnectStatus.Verifying || current.status is ConnectStatus.Connected) current
                else current.copy(code = "", status = ConnectStatus.Idle)
            }
        }.launchIn(viewModelScope)
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        val code = when (current.status) {
            ConnectStatus.Verifying, is ConnectStatus.Connected -> return
            // 오류가 난 뒤 숫자를 누르면 처음부터 새로 입력한다
            is ConnectStatus.Error -> digit.toString()
            ConnectStatus.Idle -> if (current.code.length < CODE_LENGTH) current.code + digit else return
        }
        _state.update { it.copy(code = code, status = ConnectStatus.Idle) }
        if (code.length == CODE_LENGTH) submit(code)
    }

    /** 화면을 다시 열 때 (설정 › 다시 연결) 처음 상태로 돌린다 */
    fun reset() {
        _state.update { it.copy(code = "", status = ConnectStatus.Idle, slowVerifying = false) }
    }

    fun onBackspace() = edit { it.dropLast(1) }

    fun onClear() = edit { "" }

    private fun edit(change: (String) -> String) {
        _state.update { current ->
            if (current.status is ConnectStatus.Verifying || current.status is ConnectStatus.Connected) current
            else current.copy(code = change(current.code), status = ConnectStatus.Idle)
        }
    }

    private fun submit(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(status = ConnectStatus.Verifying, slowVerifying = false) }
            val slowNotice = launch {
                delay(SLOW_NOTICE_MS)
                _state.update { it.copy(slowVerifying = true) }
            }
            val status = when (val result = pairing.pair(code)) {
                is PairingResult.Connected -> ConnectStatus.Connected(result.pairing.orgName)
                PairingResult.InvalidCode -> ConnectStatus.Error(ConnectError.INVALID)
                PairingResult.ExpiredCode -> ConnectStatus.Error(ConnectError.EXPIRED)
                PairingResult.Offline -> ConnectStatus.Error(ConnectError.OFFLINE)
                PairingResult.TooManyAttempts -> ConnectStatus.Error(ConnectError.TOO_MANY_ATTEMPTS)
                PairingResult.VehicleInOtherOrg -> ConnectStatus.Error(ConnectError.VEHICLE_IN_OTHER_ORG)
                PairingResult.NotConfigured -> ConnectStatus.Error(ConnectError.NOT_CONFIGURED)
                is PairingResult.Failed -> ConnectStatus.Error(ConnectError.UNKNOWN)
            }
            slowNotice.cancel()
            _state.update { it.copy(status = status, slowVerifying = false) }
        }
    }

    companion object {
        const val CODE_LENGTH = 6
        private const val SLOW_NOTICE_MS = 2_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DriveLogApp).container
                ConnectViewModel(
                    container.pairing,
                    container.preferences,
                    container.vehicleIdentity::vehicleId,
                    container.vehicle.drivingRestricted
                )
            }
        }
    }
}
