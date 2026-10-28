package com.altrostudio.drivelog.data.update

import android.util.Log
import com.altrostudio.drivelog.domain.update.LatestVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 관리자 웹에 올린 최신 버전 번호(app-version.json)를 받아 온다. 운행 기록·차량 정보는 보내지 않는다.
 * 오프라인이거나 실패하면 null (다음에 다시 확인)
 */
class WebLatestVersionSource(
    private val webUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): LatestVersion? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${webUrl.trimEnd('/')}/app-version.json").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<LatestVersion>(response.body?.string().orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "최신 버전 확인 실패: ${e.javaClass.simpleName}")
            null
        }
    }

    private companion object {
        const val TAG = "DriveLog/Update"
        const val CALL_TIMEOUT_SECONDS = 10L
    }
}
