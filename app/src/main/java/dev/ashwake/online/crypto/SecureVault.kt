package dev.ashwake.online.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Небольшие секреты шифруются мастер-ключом Android Keystore до записи на диск. */
@Singleton
class SecureVault @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("online_secrets", Context.MODE_PRIVATE)

    fun put(name: String, value: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val payload = cipher.iv + cipher.doFinal(value)
        preferences.edit().putString(name, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): ByteArray? {
        val encoded = preferences.getString(name, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > NONCE_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                masterKey(),
                GCMParameterSpec(128, payload.copyOfRange(0, NONCE_SIZE))
            )
            cipher.doFinal(payload.copyOfRange(NONCE_SIZE, payload.size))
        }.getOrNull()
    }

    fun remove(name: String) {
        preferences.edit().remove(name).apply()
    }

    private fun masterKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    MASTER_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_ALIAS = "ashwake.online.vault.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_SIZE = 12
    }
}
