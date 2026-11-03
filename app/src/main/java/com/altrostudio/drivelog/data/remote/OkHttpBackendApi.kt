package com.altrostudio.drivelog.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Supabase REST API(GoTrue 인증, PostgREST)를 직접 호출한다. GMS가 없는 차량에서도 HTTPS만으로 동작한다.
 * 요청에는 공개(anon) 키와 차량 계정 토큰만 쓴다.
 */
class OkHttpBackendApi(
    private val config: SupabaseConfig,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis
) : BackendApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun signInAnonymously(): DeviceSession =
        json.decodeFromString<AuthResponse>(post("/auth/v1/signup", "{}", token = null)).toSession()

    override suspend fun refresh(refreshToken: String): DeviceSession =
        json.decodeFromString<AuthResponse>(
            post("/auth/v1/token?grant_type=refresh_token", json.encodeToString(RefreshRequest(refreshToken)), token = null)
        ).toSession()

    override suspend fun pairVehicle(accessToken: String, request: PairRequest): PairResponse =
        json.decodeFromString(post("/rest/v1/rpc/pair_vehicle", json.encodeToString(request), accessToken))

    override suspend fun upsertTrips(accessToken: String, trips: List<RemoteTrip>) {
        post("/rest/v1/trips?on_conflict=id", json.encodeToString(trips), accessToken, prefer = UPSERT)
    }

    override suspend fun upsertChanges(accessToken: String, changes: List<RemoteTripChange>) {
        post("/rest/v1/trip_changes?on_conflict=id", json.encodeToString(changes), accessToken, prefer = UPSERT)
    }

    override suspend fun upsertChainHead(accessToken: String, head: RemoteChainHead) {
        post("/rest/v1/chain_heads?on_conflict=vehicle_id,chain_id", json.encodeToString(listOf(head)), accessToken, prefer = UPSERT)
    }

    override suspend fun unpairOwnVehicle(accessToken: String) {
        post("/rest/v1/rpc/unpair_own_vehicle", "{}", accessToken)
    }

    override suspend fun isVehicleDevice(accessToken: String, vehicleId: String): Boolean =
        // 차량 식별번호(VIN)를 주소에 넣지 않는다 (Pleos 앱 보안 가이드: URL 내 민감정보 금지).
        // 기기 계정은 RLS로 자기 차량만 보이므로 목록을 받아 앱에서 찾는다
        json.parseToJsonElement(get("/rest/v1/vehicles", mapOf("select" to "id"), accessToken))
            .jsonArray.any { it.jsonObject["id"]?.jsonPrimitive?.content == vehicleId }

    override suspend fun createShareLink(accessToken: String, request: ShareLinkRequest): ShareLinkResponse =
        json.decodeFromString(post("/rest/v1/rpc/create_share_link", json.encodeToString(request), accessToken))

    override suspend fun driverRoster(accessToken: String): List<RemoteDriver> =
        json.decodeFromString(post("/rest/v1/rpc/device_driver_roster", "{}", accessToken))

    override suspend fun saveComfort(accessToken: String, request: SaveComfortRequest): Boolean =
        post("/rest/v1/rpc/device_save_comfort", json.encodeToString(request), accessToken).trim() == "true"

    override suspend fun reportStatus(accessToken: String, request: ReportStatusRequest) {
        post("/rest/v1/rpc/device_report_status", json.encodeToString(request), accessToken)
    }

    private fun AuthResponse.toSession() =
        DeviceSession(accessToken, refreshToken, now() + expiresInSeconds * 1_000, user.id)

    private suspend fun post(path: String, body: String, token: String?, prefer: String? = null): String =
        execute(token, prefer) { url(config.url.trimEnd('/') + path).post(body.toRequestBody(JSON_MEDIA_TYPE)) }

    private suspend fun get(path: String, query: Map<String, String>, token: String): String =
        execute(token, prefer = null) {
            val url = (config.url.trimEnd('/') + path).toHttpUrl().newBuilder()
                .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()
            url(url).get()
        }

    private suspend fun execute(token: String?, prefer: String?, target: Request.Builder.() -> Request.Builder): String =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured) throw BackendException(BackendException.Kind.NOT_CONFIGURED, "서버 설정이 없는 빌드입니다")
            // 공개 키는 apikey 헤더에만 넣는다 (새 publishable 키는 JWT가 아니라 Authorization에 넣으면 거부된다)
            val request = Request.Builder()
                .target()
                .header("apikey", config.anonKey)
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .apply { if (prefer != null) header("Prefer", prefer) }
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> text
                        response.code == 401 -> throw BackendException(BackendException.Kind.UNAUTHORIZED, text, httpCode = 401)
                        else -> throw BackendException(BackendException.Kind.SERVER, "HTTP ${response.code}: $text", httpCode = response.code)
                    }
                }
            } catch (e: IOException) {
                throw BackendException(BackendException.Kind.NETWORK, e.message ?: "네트워크 오류", e)
            }
        }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 20L
        const val UPSERT = "resolution=merge-duplicates,return=minimal"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
