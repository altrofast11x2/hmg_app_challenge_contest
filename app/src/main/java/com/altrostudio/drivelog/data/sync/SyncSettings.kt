package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.DeviceSession

/** 연결·동기화에 필요한 저장 값. 실제 구현은 AppPreferences, 단위 테스트는 메모리 구현을 쓴다 */
interface SyncSettings {
    var deviceSession: DeviceSession?
    var pairing: Pairing?
    var pairingSkipped: Boolean
    val vin: String?
}
