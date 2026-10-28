package com.altrostudio.drivelog.domain.update

import kotlinx.serialization.Serializable

/** 배포된 최신 버전 (관리자 웹의 app-version.json) */
@Serializable
data class LatestVersion(val versionCode: Int, val versionName: String)

interface UpdateSettings {
    /** 마지막으로 최신 버전을 받아 온 시각 */
    var lastUpdateCheckAt: Long
}

/**
 * 새 버전이 나왔는지 확인한다. Pleos App Market은 자동 업데이트를 하지 않아 앱이 직접 알려야 한다 (Pleos 앱 업데이트 가이드).
 * 하루에 한 번만 확인하고, [나중에]를 고르면 다음 날 다시 묻는다
 */
class UpdateChecker(
    private val fetchLatest: suspend () -> LatestVersion?,
    private val currentVersionCode: Int,
    private val settings: UpdateSettings,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** 지금보다 새 버전이 있으면 그 버전. 확인할 때가 아니거나, 받아 오지 못했거나, 최신이면 null */
    suspend fun check(): LatestVersion? {
        if (now() - settings.lastUpdateCheckAt < CHECK_INTERVAL_MS) return null
        val latest = fetchLatest() ?: return null
        settings.lastUpdateCheckAt = now()
        return latest.takeIf { it.versionCode > currentVersionCode }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60_000L
    }
}
