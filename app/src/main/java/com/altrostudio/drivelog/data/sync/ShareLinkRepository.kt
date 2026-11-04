package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendApi
import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.ShareLinkRequest
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import kotlinx.coroutines.CancellationException
import java.time.OffsetDateTime
import java.time.ZoneId

sealed interface ShareLinkResult {
    /** QR 코드에 담을 주소와 만료 시각 */
    data class Ready(val url: String, val expiresAtMs: Long) : ShareLinkResult
    data object NotPaired : ShareLinkResult
    data object TooMany : ShareLinkResult
    data object Offline : ShareLinkResult
    data class Failed(val message: String) : ShareLinkResult
}

/**
 * 휴대폰으로 받기: 차 화면에 띄울 QR 코드의 주소를 만든다 (조직에 연결한 차량만).
 * 차에는 메일·메신저 앱이 없어서, 차는 QR만 띄우고 운전자가 휴대폰에서 운행기록부를 열어 저장·보낸다.
 * 링크는 10분 동안 그 차량의 그 기간 운행만 보여 준다 (backend 0006).
 */
class ShareLinkRepository(
    private val api: BackendApi,
    private val auth: DeviceAuth,
    private val webUrl: String,
    /** 아직 올라가지 않은 운행도 휴대폰에서 보이도록 링크를 만들기 전에 올린다 */
    private val syncFirst: suspend () -> Unit = {}
) {
    /** 차에서 고른 기간(차의 시간대 기준)의 링크. 휴대폰 화면도 같은 기간 이름을 보이도록 날짜를 주소에 함께 담는다 */
    suspend fun create(period: LogbookPeriod, zone: ZoneId): ShareLinkResult = try {
        syncFirst()
        val (fromMs, toMs) = period.range(zone)
        val response = auth.withToken { api.createShareLink(it, ShareLinkRequest(fromMs, toMs)) }
        when (response.status) {
            "OK" -> ShareLinkResult.Ready(
                url = shareUrl(webUrl, checkNotNull(response.token), period),
                // 서버 시각은 "2026-10-27T05:34:12.123+00:00" 꼴이다
                expiresAtMs = OffsetDateTime.parse(checkNotNull(response.expiresAt)).toInstant().toEpochMilli()
            )
            "NOT_PAIRED" -> ShareLinkResult.NotPaired
            "TOO_MANY" -> ShareLinkResult.TooMany
            else -> ShareLinkResult.Failed(response.status)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackendException) {
        if (e.kind == BackendException.Kind.NETWORK) ShareLinkResult.Offline else ShareLinkResult.Failed(e.message.orEmpty())
    } catch (e: Exception) {
        ShareLinkResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    companion object {
        /** 토큰은 주소의 # 뒤에 둔다. # 뒤는 브라우저가 서버로 보내지 않아 웹 서버 기록에 남지 않는다 */
        fun shareUrl(webUrl: String, token: String, period: LogbookPeriod): String =
            "${webUrl.trimEnd('/')}/app.html#share=$token&from=${period.start}&to=${period.end}"
    }
}
