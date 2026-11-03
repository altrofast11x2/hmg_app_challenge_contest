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
    END_ODOMETER(false),

    /** 운행기록부 ④사용자(성명·부서). 운행을 마칠 때 남기고, 나중에 바꾸면 다시 남긴다 */
    DRIVER(false),

    /** 업무 내용(음성 업무 일지). 거래처 이름 같은 내용이 들어가므로 비업무로 바뀌면 방문처처럼 지운다 */
    WORK_NOTE(true)
}
