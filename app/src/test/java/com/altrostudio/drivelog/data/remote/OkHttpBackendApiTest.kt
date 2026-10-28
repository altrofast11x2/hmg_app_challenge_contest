package com.altrostudio.drivelog.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 실제 HTTP 요청 형식(경로, 헤더, JSON)이 Supabase REST API에 맞는지 가짜 웹서버로 확인한다 */
class OkHttpBackendApiTest {

    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun api(config: SupabaseConfig = SupabaseConfig(server.url("/").toString(), "anon-key")) =
        OkHttpBackendApi(config, now = { 1_000L })

    private suspend fun failureKind(block: suspend () -> Unit): BackendException.Kind? = try {
        block()
        null
    } catch (e: BackendException) {
        e.kind
    }

    @Test
    fun `익명 로그인은 공개 키로 signup을 부르고 만료 시각을 계산한다`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"access_token":"a","refresh_token":"r","expires_in":3600,"token_type":"bearer","user":{"id":"u1","is_anonymous":true}}""")
        )

        val session = api().signInAnonymously()

        assertEquals(DeviceSession("a", "r", 1_000L + 3_600_000L, "u1"), session)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/signup", request.path)
        assertEquals("anon-key", request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `연결 요청은 차량 토큰으로 pair_vehicle 함수를 부른다`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"OK","org_id":"o1","org_name":"데모","vehicle_id":"v1","driver_id":"d1","driver_name":"기본 운전자"}"""))

        val response = api().pairVehicle("device-token", PairRequest("123456", "v1", null))

        assertEquals("데모", response.orgName)
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/pair_vehicle", request.path)
        assertEquals("Bearer device-token", request.getHeader("Authorization"))
        assertEquals("""{"p_code":"123456","p_vehicle_id":"v1","p_vin":null}""", request.body.readUtf8())
    }

    @Test
    fun `운행은 id 기준으로 덮어쓰고 빈 값도 null로 보내 서버 값을 지운다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        val trip = RemoteTrip(
            id = "t1", orgId = "o1", vehicleId = "v1", driverId = null, startedAtMs = 1, endedAtMs = 2,
            startOdometerKm = 100.0, endOdometerKm = 112.3, odometerSource = "VEHICLE", startSocPercent = null,
            endSocPercent = null, efficiency = null, efficiencyType = null, startAddress = null, endAddress = null,
            visitNote = null, purpose = "PERSONAL", chainId = "a".repeat(64), chainSeq = 1, prevHash = "0".repeat(64),
            hash = "a".repeat(64)
        )

        api().upsertTrips("device-token", listOf(trip))

        val request = server.takeRequest()
        assertEquals("/rest/v1/trips?on_conflict=id", request.path)
        assertEquals("resolution=merge-duplicates,return=minimal", request.getHeader("Prefer"))
        val body = request.body.readUtf8()
        assertTrue(body.startsWith("["))
        assertTrue(body.contains("\"start_address\":null"))
        assertTrue(body.contains("\"end_odometer_km\":112.3"))
        assertTrue(body.contains("\"chain_seq\":1"))
        assertTrue(body.contains("\"chain_id\":\"${"a".repeat(64)}\""))
    }

    @Test
    fun `체인 머리는 차량과 체인 ID 기준으로 덮어쓴다 (같은 차의 옛 체인 머리는 남는다)`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        api().upsertChainHead("device-token", RemoteChainHead("v1", "o1", "c1", 2, "h"))

        val request = server.takeRequest()
        assertEquals("/rest/v1/chain_heads?on_conflict=vehicle_id,chain_id", request.path)
        assertEquals("""[{"vehicle_id":"v1","org_id":"o1","chain_id":"c1","seq":2,"hash":"h"}]""", request.body.readUtf8())
    }

    @Test
    fun `기기 확인은 보이는 차량 목록에 이 차가 있으면 true, 서버에서 연결이 끊겨 없으면 false`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id":"KMH 1&2"}]"""))
        server.enqueue(MockResponse().setBody("""[{"id":"OTHER-VIN"}]"""))
        server.enqueue(MockResponse().setBody("[]"))

        assertTrue(api().isVehicleDevice("device-token", "KMH 1&2"))
        assertFalse(api().isVehicleDevice("device-token", "KMH 1&2"))
        assertFalse(api().isVehicleDevice("device-token", "KMH 1&2"))

        // 차량 식별번호(VIN)는 주소에 넣지 않는다
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/rest/v1/vehicles?select=id", request.path)
        assertEquals("Bearer device-token", request.getHeader("Authorization"))
        assertEquals("anon-key", request.getHeader("apikey"))
    }

    @Test
    fun `오류에는 서버가 답한 HTTP 상태 코드가 담긴다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"42501"}"""))

        val error = try {
            api().upsertTrips("t", emptyList())
            null
        } catch (e: BackendException) {
            e
        }

        assertEquals(BackendException.Kind.SERVER, error?.kind)
        assertEquals(403, error?.httpCode)
    }

    @Test
    fun `토큰이 거부되면 UNAUTHORIZED, 서버 오류면 SERVER`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"JWT expired"}"""))
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(BackendException.Kind.UNAUTHORIZED, failureKind { api().upsertChainHead("t", RemoteChainHead("v1", "o1", "c1", 1, "h")) })
        assertEquals(BackendException.Kind.SERVER, failureKind { api().upsertChainHead("t", RemoteChainHead("v1", "o1", "c1", 1, "h")) })
    }

    @Test
    fun `서버에 닿지 않으면 NETWORK, 설정이 없으면 NOT_CONFIGURED`() = runTest {
        val unreachable = SupabaseConfig("http://127.0.0.1:1", "anon-key")
        assertEquals(BackendException.Kind.NETWORK, failureKind { api(unreachable).signInAnonymously() })
        assertEquals(BackendException.Kind.NOT_CONFIGURED, failureKind { api(SupabaseConfig("", "")).signInAnonymously() })
    }
}
