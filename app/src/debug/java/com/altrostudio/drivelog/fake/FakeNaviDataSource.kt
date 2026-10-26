package com.altrostudio.drivelog.fake

import com.altrostudio.drivelog.data.navi.NaviArrival
import com.altrostudio.drivelog.data.navi.NaviDataSource
import com.altrostudio.drivelog.data.navi.NaviPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** debug 빌드·단위 테스트 전용 내비 */
class FakeNaviDataSource : NaviDataSource {

    var place: NaviPlace? = NaviPlace(name = null, address = "서울 강남구 테헤란로 1", latitude = 37.5, longitude = 127.0)
    var destinationPlace: NaviPlace? = null
    val arrivalEvents = MutableSharedFlow<NaviArrival>(extraBufferCapacity = 8)

    override suspend fun currentPlace(): NaviPlace? = place

    override suspend fun destination(): NaviPlace? = destinationPlace

    override val arrivals: Flow<NaviArrival> = arrivalEvents
}
