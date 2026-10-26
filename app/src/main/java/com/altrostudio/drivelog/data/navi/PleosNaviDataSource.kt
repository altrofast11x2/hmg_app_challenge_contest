package com.altrostudio.drivelog.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos NaviHelper SDK. 조회 함수(get*Info)는 값을 바로 돌려주지 않고 리스너로 응답하므로,
 * 요청을 보낸 뒤 리스너로 오는 첫 응답을 기다린다.
 */
class PleosNaviDataSource(context: Context) : NaviDataSource, AutoCloseable {

    private val naviHelper = NaviHelper(context.applicationContext)
    private val connected = CompletableDeferred<Boolean>()
    private val locationReplies = MutableSharedFlow<CurrentLocationInfo>(extraBufferCapacity = 4)
    private val destinationReplies = MutableSharedFlow<DestinationInfo>(extraBufferCapacity = 4)
    private val arrivalEvents = MutableSharedFlow<NaviArrival>(extraBufferCapacity = 16)

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            locationReplies.tryEmit(currentLocationInfo)
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            destinationReplies.tryEmit(destinationInfo)
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            arrivalEvents.tryEmit(
                NaviArrival(NaviArrival.Kind.DESTINATION, destinationArrivedInfo.name, destinationArrivedInfo.address)
            )
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            arrivalEvents.tryEmit(
                NaviArrival(NaviArrival.Kind.WAYPOINT, waypointArrivedInfo.name, waypointArrivedInfo.address)
            )
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "NaviHelper 오류: $errorCode")
        }
    }

    init {
        naviHelper.initialize(
            onConnected = { connected.complete(true) },
            onFailed = { error ->
                Log.w(TAG, "NaviHelper 연결 실패", error)
                connected.complete(false)
            }
        )
        naviHelper.addListener(listener)
    }

    override val arrivals: Flow<NaviArrival> = arrivalEvents.asSharedFlow()

    override suspend fun currentPlace(): NaviPlace? =
        request(locationReplies) { naviHelper.getCurrentLocationInfo() }
            ?.let { NaviPlace(name = null, address = it.address, latitude = it.latitude, longitude = it.longitude) }

    // 경로 안내 중이 아니면 오류 대신 이름·주소가 빈 값으로 온다
    override suspend fun destination(): NaviPlace? =
        request(destinationReplies) { naviHelper.getDestinationInfo() }
            ?.takeIf { it.address.isNotBlank() }
            ?.let { NaviPlace(name = it.name, address = it.address, latitude = it.latitude, longitude = it.longitude) }

    override fun close() {
        naviHelper.removeListener(listener)
        naviHelper.release()
    }

    /**
     * 응답을 먼저 구독한 다음 요청을 보낸다. 연결 실패나 응답 없음이면 null.
     * 차량이 막 켜졌을 때는 내비 서비스 연결이 3초 넘게 걸려서, 연결과 응답 대기 시간을 따로 잡는다.
     */
    private suspend fun <T> request(replies: Flow<T>, send: () -> Unit): T? {
        val isConnected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } ?: false
        if (!isConnected) return null
        return withTimeoutOrNull(REPLY_TIMEOUT_MS) {
            coroutineScope {
                val reply = async(start = CoroutineStart.UNDISPATCHED) { replies.first() }
                send()
                reply.await()
            }
        }
    }

    private companion object {
        const val TAG = "DriveLog/Navi"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REPLY_TIMEOUT_MS = 3_000L
    }
}
