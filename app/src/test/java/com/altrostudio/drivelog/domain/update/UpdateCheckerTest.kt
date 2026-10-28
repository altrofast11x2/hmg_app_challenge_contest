package com.altrostudio.drivelog.domain.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    private class MemoryUpdateSettings : UpdateSettings {
        override var lastUpdateCheckAt = 0L
    }

    private val settings = MemoryUpdateSettings()
    private var now = 100L * UpdateChecker.CHECK_INTERVAL_MS
    private var latest: LatestVersion? = LatestVersion(3, "0.3.0")
    private var fetches = 0
    private val checker = UpdateChecker({ fetches++; latest }, currentVersionCode = 2, settings = settings, now = { now })

    @Test
    fun `새 버전이 올라와 있으면 알려 주고 확인한 시각을 남긴다`() = runTest {
        assertEquals(LatestVersion(3, "0.3.0"), checker.check())
        assertEquals(now, settings.lastUpdateCheckAt)
    }

    @Test
    fun `하루가 지나기 전에는 다시 받아 오지 않는다 (나중에를 고르면 다음 날 다시 묻는다)`() = runTest {
        checker.check()
        now += UpdateChecker.CHECK_INTERVAL_MS - 1
        assertNull(checker.check())
        assertEquals(1, fetches)

        now += 1
        assertEquals(LatestVersion(3, "0.3.0"), checker.check())
        assertEquals(2, fetches)
    }

    @Test
    fun `지금 버전이 최신이면 아무것도 묻지 않는다`() = runTest {
        latest = LatestVersion(2, "0.2.0")
        assertNull(checker.check())
    }

    @Test
    fun `받아 오지 못하면(오프라인) 확인하지 않은 것으로 두고 다음에 다시 시도한다`() = runTest {
        latest = null
        assertNull(checker.check())
        assertEquals(0L, settings.lastUpdateCheckAt)

        latest = LatestVersion(3, "0.3.0")
        assertEquals(LatestVersion(3, "0.3.0"), checker.check())
    }
}
