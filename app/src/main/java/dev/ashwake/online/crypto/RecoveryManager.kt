package dev.ashwake.online.crypto

import android.util.Base64
import dev.ashwake.online.model.OnlineSpace
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoveryManager @Inject constructor(
    private val vault: SecureVault,
    private val spaces: SpaceCrypto
) {
    fun createCode(): String = ByteArray(32).also(SecureRandom()::nextBytes)
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        .also { vault.put(CODE, it.toByteArray()) }

    fun storedCode(): String? = vault.get(CODE)?.let(::String)

    fun tokenHash(code: String): String = MessageDigest.getInstance("SHA-256")
        .digest(code.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun encryptBundle(code: String, onlineSpaces: List<OnlineSpace>): String {
        val payload = JSONObject().apply {
            put("version", 1)
            put("spaces", JSONArray().apply {
                onlineSpaces.forEach { space ->
                    val key = spaces.key(space.id, space.keyVersion) ?: return@forEach
                    put(JSONObject().apply {
                        put("id", space.id)
                        put("keyVersion", space.keyVersion)
                        put("key", key.b64())
                    })
                }
            })
        }.toString().toByteArray()
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, recoveryKey(code), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        return JSONObject().apply {
            put("version", 1)
            put("nonce", nonce.b64())
            put("ciphertext", cipher.doFinal(payload).b64())
        }.toString()
    }

    fun restoreBundle(code: String, encrypted: String) {
        val root = JSONObject(encrypted)
        require(root.getInt("version") == 1)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            recoveryKey(code),
            GCMParameterSpec(128, root.getString("nonce").decode64())
        )
        cipher.updateAAD(AAD)
        val bundle = JSONObject(String(cipher.doFinal(root.getString("ciphertext").decode64())))
        require(bundle.getInt("version") == 1)
        val values = bundle.getJSONArray("spaces")
        repeat(values.length()) { index ->
            val value = values.getJSONObject(index)
            spaces.importKey(value.getString("id"), value.getInt("keyVersion"), value.getString("key").decode64())
        }
        vault.put(CODE, code.toByteArray())
    }

    private fun recoveryKey(code: String) = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(code.toByteArray()),
        "AES"
    )

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode64(): ByteArray = Base64.decode(this, Base64.DEFAULT)

    private companion object {
        const val CODE = "recovery.code.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val AAD = "ashwake-recovery-v1".toByteArray()
    }
}
