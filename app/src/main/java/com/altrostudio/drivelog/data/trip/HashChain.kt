package com.altrostudio.drivelog.data.trip

import java.security.MessageDigest
import java.util.Locale

/**
 * 해시 체인의 계산 규칙. 각 기록의 해시 = SHA-256(이전 해시 + "\n" + 정규화한 필드).
 * 관리자 웹(M2)에서도 같은 규칙으로 다시 계산해 변조를 찾으므로 형식을 바꾸면 안 된다 (docs/integrity.md).
 */
object HashChain {

    /** 체인 첫 기록의 이전 해시 */
    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

    private const val COMMITMENT_PREFIX = "sha256:"

    fun hash(prevHash: String, fields: List<Pair<String, String?>>): String =
        sha256Hex("$prevHash\n${canonical(fields)}")

    /** key=value 줄을 정해진 순서로 잇는다. null은 빈 값, 값 안의 역슬래시·줄바꿈은 이스케이프한다 */
    fun canonical(fields: List<Pair<String, String?>>): String =
        fields.joinToString("\n") { (key, value) -> "$key=${value.orEmpty().escape()}" }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * 지울 수 있는 값(위치)은 원문 대신 해시값으로 체인에 묶는다.
     * 원문을 지울 때 이 값으로 바꿔 두면 체인은 그대로 검증된다. 이미 바꾼 값은 다시 해시하지 않는다.
     */
    fun commitment(value: String?): String? = when {
        value == null -> null
        value.startsWith(COMMITMENT_PREFIX) -> value
        else -> COMMITMENT_PREFIX + sha256Hex(value)
    }

    /** km는 소수 첫째 자리까지, 기기 언어 설정과 상관없이 점(.)으로 쓴다 */
    fun km(value: Double?): String? = value?.let { String.format(Locale.ROOT, "%.1f", it) }

    private fun String.escape() = replace("\\", "\\\\").replace("\n", "\\n")
}
