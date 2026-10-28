package com.altrostudio.drivelog.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretStoreTest {

    /** 뒤집기만 하는 가짜 암호화. 형식이 다르면 풀지 못한다 */
    private class ReversingCipher : StringCipher {
        override fun encrypt(plain: String) = "R" + plain.reversed()
        override fun decrypt(encoded: String) = if (encoded.startsWith("R")) encoded.drop(1).reversed() else null
    }

    private val saved = mutableMapOf<String, String?>()
    private val store = SecretStore(ReversingCipher(), read = { saved[it] }, write = { key, value -> saved[key] = value })

    @Test
    fun `저장소에는 암호화한 값만 남고 읽으면 원래 값이 나온다`() {
        store.put("vin", "KMHXX00XXXX000000")

        assertFalse(saved.getValue("vin")!!.contains("KMHXX00XXXX000000"))
        assertEquals("KMHXX00XXXX000000", store.get("vin"))
    }

    @Test
    fun `암호화 전 버전이 남긴 평문은 그대로 읽히고 그 자리에서 암호화된다`() {
        saved["device_session"] = """{"accessToken":"a"}"""

        assertEquals("""{"accessToken":"a"}""", store.get("device_session"))
        assertTrue(saved.getValue("device_session")!!.startsWith("enc:v1:"))
        assertEquals("""{"accessToken":"a"}""", store.get("device_session"))
    }

    @Test
    fun `풀 수 없는 값은 없는 것으로 보고, null을 저장하면 지운다`() {
        saved["pairing"] = "enc:v1:깨진값"
        assertNull(store.get("pairing"))

        store.put("vin", "X")
        store.put("vin", null)
        assertNull(saved["vin"])
        assertNull(store.get("vin"))
    }
}
