package com.deepseek.personal.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 加密：密钥保存在 Android Keystore（硬件级安全），
 * 数据以 AES-GCM 密文存 DataStore；旧明文自动迁移。
 */
object CryptoManager {

    private const val KEY_ALIAS = "deepseek_app_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isBlank()) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX +
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored // 旧明文数据，直接迁移
        val parts = stored.removePrefix(PREFIX).split(":")
        if (parts.size != 2) return ""
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val enc = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(enc), Charsets.UTF_8)
        } catch (_: Exception) {
            "" // 解密失败（密钥丢失/数据损坏）：返回空，提示重新填写
        }
    }
}
