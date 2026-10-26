package com.altrostudio.drivelog.data.trip

/** 운행 진행 상태 */
enum class TripStatus { DRIVING, PARKED, COMPLETED }

/** 국세청 업무용승용차 운행기록부의 구분: 출퇴근 / 일반업무 / 비업무 */
enum class TripPurpose { UNCLASSIFIED, COMMUTE, BUSINESS, PERSONAL }

/** 계기판 값의 출처. 사람이 고친 값은 원본을 덮어쓰지 않고 수정 이력(trip_changes)에 남는다 */
enum class ValueSource { VEHICLE }

/** 서버 동기화 상태 */
enum class SyncState { PENDING, SYNCED }

/**
 * 수정 이력으로 남기는 항목.
 * [isLocation]인 값은 비업무로 바뀌면 지워야 하므로, 해시 체인에는 원문 대신 해시값으로 묶는다.
 */
enum class ChangeField(val isLocation: Boolean) {
    PURPOSE(false),
    VISIT_NOTE(true),
    START_ODOMETER(false),
    END_ODOMETER(false)
}
