package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.ShareLinkResponse
import com.altrostudio.drivelog.domain.logbook.LogbookPeriod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

class ShareLinkRepositoryTest {

    private val api = FakeBackendApi()
    private val settings = MemorySyncSettings(vin = null)
    private var synced = 0
    private val repository = ShareLinkRepository(
        api, DeviceAuth(api, settings, now = { 0L }), webUrl = "https://example.test/", syncFirst = { synced++ }
    )

    private val zone = ZoneId.of("Asia/Seoul")
    private val october = LogbookPeriod.ofMonth(YearMonth.of(2026, 10))

    @Test
    fun `링크를 만들기 전에 올리지 않은 운행부터 올리고, 토큰과 기간은 주소의 샵 뒤에 넣는다`() = runTest {
        val result = repository.create(october, zone)

        assertEquals(1, synced)
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli(), api.lastShareRequest?.fromMs)
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli(), api.lastShareRequest?.toMs)
        val ready = result as ShareLinkResult.Ready
        // 휴대폰 화면도 차와 같은 기간 이름을 보이도록 (차와 휴대폰의 시간대가 달라도)
        assertEquals("https://example.test/app.html#share=token-1&from=2026-10-01&to=2026-10-31", ready.url)
        assertEquals(OffsetDateTime.parse("2026-10-27T05:34:12.123456+00:00").toInstant().toEpochMilli(), ready.expiresAtMs)
    }

    @Test
    fun `연결이 풀렸거나 너무 많이 만들면 이유를 알린다`() = runTest {
        api.shareResponse = ShareLinkResponse(status = "NOT_PAIRED")
        assertEquals(ShareLinkResult.NotPaired, repository.create(october, zone))
        api.shareResponse = ShareLinkResponse(status = "TOO_MANY")
        assertEquals(ShareLinkResult.TooMany, repository.create(october, zone))
    }

    @Test
    fun `인터넷이 없거나 예상 밖의 오류여도 앱이 멈추지 않는다`() = runTest {
        api.failure = BackendException(BackendException.Kind.NETWORK, "offline")
        assertEquals(ShareLinkResult.Offline, repository.create(october, zone))
        api.failure = SecurityException("denied")
        assertTrue(repository.create(october, zone) is ShareLinkResult.Failed)
    }
}
