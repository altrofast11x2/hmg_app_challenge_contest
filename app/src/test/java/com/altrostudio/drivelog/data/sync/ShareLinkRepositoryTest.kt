package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.ShareLinkResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ShareLinkRepositoryTest {

    private val api = FakeBackendApi()
    private val settings = MemorySyncSettings(vin = null)
    private var synced = 0
    private val repository = ShareLinkRepository(
        api, DeviceAuth(api, settings, now = { 0L }), webUrl = "https://example.test/", syncFirst = { synced++ }
    )

    @Test
    fun `링크를 만들기 전에 올리지 않은 운행부터 올리고, 토큰은 주소의 샵 뒤에 넣는다`() = runTest {
        val result = repository.create(fromMs = 1_000, toMs = 2_000)

        assertEquals(1, synced)
        assertEquals(1_000L, api.lastShareRequest?.fromMs)
        assertEquals(2_000L, api.lastShareRequest?.toMs)
        val ready = result as ShareLinkResult.Ready
        assertEquals("https://example.test/#share=token-1", ready.url)
        assertEquals(OffsetDateTime.parse("2026-10-27T05:34:12.123456+00:00").toInstant().toEpochMilli(), ready.expiresAtMs)
    }

    @Test
    fun `연결이 풀렸거나 너무 많이 만들면 이유를 알린다`() = runTest {
        api.shareResponse = ShareLinkResponse(status = "NOT_PAIRED")
        assertEquals(ShareLinkResult.NotPaired, repository.create(0, 1))
        api.shareResponse = ShareLinkResponse(status = "TOO_MANY")
        assertEquals(ShareLinkResult.TooMany, repository.create(0, 1))
    }

    @Test
    fun `인터넷이 없거나 예상 밖의 오류여도 앱이 멈추지 않는다`() = runTest {
        api.failure = BackendException(BackendException.Kind.NETWORK, "offline")
        assertEquals(ShareLinkResult.Offline, repository.create(0, 1))
        api.failure = SecurityException("denied")
        assertTrue(repository.create(0, 1) is ShareLinkResult.Failed)
    }
}
