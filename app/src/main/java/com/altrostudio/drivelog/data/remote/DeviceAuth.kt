package com.altrostudio.drivelog.data.remote

import com.altrostudio.drivelog.data.sync.SyncSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 차량용 기기 계정. 차 안에서 비밀번호를 입력하지 않도록 익명 로그인으로 만들고, 연결 코드로 조직에 등록한다.
 * 토큰은 만료 1분 전에 미리 새로 받는다.
 */
class DeviceAuth(
    private val api: BackendApi,
    private val preferences: SyncSettings,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()

    suspend fun accessToken(): String = mutex.withLock {
        val session = preferences.deviceSession
        val fresh = when {
            session == null -> api.signInAnonymously()
            session.expiresAtMs - now() < REFRESH_MARGIN_MS -> refreshOrSignIn(session)
            else -> session
        }
        if (fresh != session) preferences.deviceSession = fresh
        fresh.accessToken
    }

    /**
     * 기기 계정이 서버에서 지워졌으면(개발자가 기업을 삭제한 경우 등) 갱신 토큰이 거부된다(400·401).
     * 전에는 그 뒤로 동기화와 다시 연결이 모두 실패했다. 새 기기 계정을 만들어 연결 코드로 다시 연결할 수 있게 한다 (2026-10-27 점검)
     */
    private suspend fun refreshOrSignIn(session: DeviceSession): DeviceSession = try {
        api.refresh(session.refreshToken)
    } catch (e: BackendException) {
        if (e.httpCode == 400 || e.httpCode == 401) api.signInAnonymously() else throw e
    }

    /** 서버가 토큰을 거부하면(401) 새 토큰으로 한 번 더 시도한다 */
    suspend fun <T> withToken(block: suspend (accessToken: String) -> T): T = try {
        block(accessToken())
    } catch (e: BackendException) {
        if (e.kind != BackendException.Kind.UNAUTHORIZED) throw e
        mutex.withLock { preferences.deviceSession = preferences.deviceSession?.copy(expiresAtMs = 0) }
        block(accessToken())
    }

    private companion object {
        const val REFRESH_MARGIN_MS = 60_000L
    }
}
