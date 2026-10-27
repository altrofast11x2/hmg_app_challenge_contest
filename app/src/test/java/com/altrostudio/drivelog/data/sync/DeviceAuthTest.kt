package com.altrostudio.drivelog.data.sync

import com.altrostudio.drivelog.data.remote.BackendException
import com.altrostudio.drivelog.data.remote.DeviceAuth
import com.altrostudio.drivelog.data.remote.DeviceSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAuthTest {

    private val api = FakeBackendApi()
    private val settings = MemorySyncSettings()
    private val auth = DeviceAuth(api, settings, now = { 1_000L })

    @Test
    fun `기기 계정이 서버에서 지워져 갱신 토큰이 거부되면(400) 새 기기 계정을 만든다`() = runTest {
        settings.deviceSession = DeviceSession("old-access", "old-refresh", expiresAtMs = 0, userId = "deleted-device")
        api.refreshFailure = BackendException(
            BackendException.Kind.SERVER, "HTTP 400: refresh_token_not_found", httpCode = 400
        )

        val token = auth.accessToken()

        assertEquals(1, api.signIns)
        assertNotEquals("old-access", token)
        assertEquals(token, settings.deviceSession?.accessToken)
    }

    @Test
    fun `서버 오류(500)로 갱신이 실패하면 기기 계정을 바꾸지 않고 실패를 알린다`() = runTest {
        settings.deviceSession = DeviceSession("old-access", "old-refresh", expiresAtMs = 0, userId = "device")
        api.refreshFailure = BackendException(BackendException.Kind.SERVER, "HTTP 500", httpCode = 500)

        val error = runCatching { auth.accessToken() }.exceptionOrNull()

        assertTrue(error is BackendException)
        assertEquals(0, api.signIns)
        assertEquals("device", settings.deviceSession?.userId)
    }
}
