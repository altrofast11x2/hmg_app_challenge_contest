package com.altrostudio.drivelog.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class HashChainTest {

    @Test
    fun `SHA-256은 표준 테스트 벡터와 같다`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashChain.sha256Hex("abc")
        )
    }

    @Test
    fun `같은 입력은 같은 해시, 값이 하나만 달라도 다른 해시`() {
        val fields = listOf("a" to "1", "b" to null)
        assertEquals(HashChain.hash(HashChain.GENESIS, fields), HashChain.hash(HashChain.GENESIS, fields))
        assertNotEquals(
            HashChain.hash(HashChain.GENESIS, fields),
            HashChain.hash(HashChain.GENESIS, listOf("a" to "2", "b" to null))
        )
        assertNotEquals(HashChain.hash(HashChain.GENESIS, fields), HashChain.hash("1".repeat(64), fields))
    }

    @Test
    fun `줄바꿈이 든 값으로 다른 필드를 흉내 낼 수 없다`() {
        assertNotEquals(
            HashChain.canonical(listOf("a" to "1\nb=2")),
            HashChain.canonical(listOf("a" to "1", "b" to "2"))
        )
    }

    @Test
    fun `km는 기기 언어와 상관없이 소수 첫째 자리까지 점으로 쓴다`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("100.5", HashChain.km(100.5))
            assertEquals("12.0", HashChain.km(12.0))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `commitment는 이미 해시한 값을 다시 해시하지 않는다`() {
        val once = HashChain.commitment("서울 강남구")
        assertEquals(once, HashChain.commitment(once))
        assertEquals(null, HashChain.commitment(null))
    }
}
