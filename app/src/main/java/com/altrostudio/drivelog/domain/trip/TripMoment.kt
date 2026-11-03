package com.altrostudio.drivelog.domain.trip

import com.altrostudio.drivelog.data.trip.TripPurpose

/**
 * 운행 기록 엔진이 저장을 마친 뒤 알리는 순간. 차 안 연동(무드등 신호, 하차 점검, 운전자 설정 기억)이 이것을 듣는다.
 * 음성 안내([com.altrostudio.drivelog.domain.voice.TripAnnouncement])와 따로 둔다 (소리를 꺼도 연동은 동작한다)
 */
sealed interface TripMoment {
    val tripId: String

    data class Started(override val tripId: String) : TripMoment

    /** P로 정차했다. [awaitingClassification]이면 분류 화면이 떠 있다 (주행 중 음성으로 용도를 정했으면 false) */
    data class Parked(override val tripId: String, val awaitingClassification: Boolean) : TripMoment

    /** 정차했다가 같은 운행으로 다시 출발했다 */
    data class Resumed(override val tripId: String) : TripMoment

    /** 운행을 마쳤다. [purpose]가 null이면 정차 대기 시간이 지나 미분류로 마쳤다 */
    data class Completed(override val tripId: String, val purpose: TripPurpose?) : TripMoment
}
