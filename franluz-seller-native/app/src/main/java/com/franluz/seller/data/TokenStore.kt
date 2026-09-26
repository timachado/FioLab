package com.franluz.seller.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("franluz_seller_secure", Context.MODE_PRIVATE)
    private val alias = "franluz_seller_session_v2"

    fun save(access: String, refresh: String) {
        prefs.edit()
            .putString("access", encrypt(access))
            .putString("refresh", encrypt(refresh))
            .apply()
    }

    fun access(): String? = decryptSafe(prefs.getString("access", null))
    fun refresh(): String? = decryptSafe(prefs.getString("refresh", null))

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun decryptSafe(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching { decrypt(value) }.getOrElse {
            clear()
            null
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(value: String): String {
        val pieces = value.split(":", limit = 2)
        require(pieces.size == 2)

        val iv = Base64.decode(pieces[0], Base64.NO_WRAP)
        val data = Base64.decode(pieces[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }
}
