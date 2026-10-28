package com.altrostudio.drivelog.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 문자열 암호화 (테스트에서는 가짜로 바꿔 끼운다) */
interface StringCipher {
    fun encrypt(plain: String): String

    /** 풀 수 없으면(키가 사라졌거나 값이 깨짐) null */
    fun decrypt(encoded: String): String?
}

/**
 * 키-값 저장소(SharedPreferences) 위에 암호화를 얹는다 (Pleos 앱 보안 가이드: 기기에 저장하는 민감정보는 암호화).
 * 암호화 전 버전이 평문으로 저장한 값은 처음 읽을 때 그대로 돌려주고 암호화해 다시 저장한다
 */
class SecretStore(
    private val cipher: StringCipher,
    private val read: (key: String) -> String?,
    private val write: (key: String, value: String?) -> Unit
) {

    fun get(key: String): String? {
        val stored = read(key) ?: return null
        if (stored.startsWith(PREFIX)) return cipher.decrypt(stored.removePrefix(PREFIX))
        put(key, stored)
        return stored
    }

    fun put(key: String, value: String?) = write(key, value?.let { PREFIX + cipher.encrypt(it) })

    private companion object {
        /** 암호화한 값의 표시. 형식을 바꾸면 v2로 올린다 */
        const val PREFIX = "enc:v1:"
    }
}

/**
 * Android Keystore의 AES-256-GCM 키로 암호화한다. 키는 기기 밖으로 꺼낼 수 없고(코드·파일에 키를 두지 않음),
 * IV는 암호화할 때마다 Keystore가 새로 만든다. 저장 형식: Base64(IV 12바이트 + 암호문·인증 태그)
 */
class KeystoreCipher(private val alias: String = "drivelog_storage") : StringCipher {

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? = try {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        }
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
