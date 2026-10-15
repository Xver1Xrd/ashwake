package dev.ashwake.online.crypto

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceCrypto @Inject constructor(
    private val identity: DeviceIdentity,
    private val vault: SecureVault
) {
    fun key(spaceId: String, version: Int): ByteArray? = vault.get(keyName(spaceId, version))

    fun importKey(spaceId: String, version: Int, key: ByteArray) {
        require(key.size == 32)
        vault.put(keyName(spaceId, version), key)
    }

    fun createKey(spaceId: String, version: Int): ByteArray =
        ByteArray(32).also {
            SecureRandom().nextBytes(it)
            vault.put(keyName(spaceId, version), it)
        }

    fun encrypt(spaceId: String, version: Int, plaintext: String): String {
        val key = key(spaceId, version) ?: error("Нет ключа пространства")
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("$spaceId:$version".toByteArray())
        return JSONObject().apply {
            put("v", 1)
            put("n", nonce.b64())
            put("c", cipher.doFinal(plaintext.toByteArray()).b64())
        }.toString()
    }

    fun decrypt(spaceId: String, version: Int, payload: String): String {
        val key = key(spaceId, version) ?: error("Нет ключа пространства")
        val root = JSONObject(payload)
        require(root.getInt("v") == 1)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, root.getString("n").b64decode())
        )
        cipher.updateAAD("$spaceId:$version".toByteArray())
        return String(cipher.doFinal(root.getString("c").b64decode()))
    }

    fun wrap(spaceId: String, version: Int, recipientPublicPem: String): String {
        val spaceKey = key(spaceId, version) ?: error("Нет ключа пространства")
        val recipient = decodePem(recipientPublicPem)
        val ephemeral = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(ephemeral.private)
            doPhase(recipient, true)
            generateSecret()
        }
        val wrappingKey = hkdf(secret, "$spaceId:$version".toByteArray())
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, nonce))
        return JSONObject().apply {
            put("v", 1)
            put("epk", ephemeral.public.encoded.b64())
            put("n", nonce.b64())
            put("c", cipher.doFinal(spaceKey).b64())
        }.toString()
    }

    fun unwrap(spaceId: String, version: Int, envelope: String) {
        val root = JSONObject(envelope)
        require(root.getInt("v") == 1)
        val secret = identity.agree(identity.decodePublic(root.getString("epk").b64decode()))
        val wrappingKey = hkdf(secret, "$spaceId:$version".toByteArray())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(wrappingKey, "AES"),
            GCMParameterSpec(128, root.getString("n").b64decode())
        )
        vault.put(keyName(spaceId, version), cipher.doFinal(root.getString("c").b64decode()))
    }

    private fun decodePem(pem: String) = KeyFactory.getInstance("EC").generatePublic(
        X509EncodedKeySpec(
            pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .filterNot(Char::isWhitespace)
                .b64decode()
        )
    )

    private fun hkdf(secret: ByteArray, info: ByteArray): ByteArray {
        val salt = ByteArray(32)
        val extract = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(salt, "HmacSHA256"))
            doFinal(secret)
        }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(extract, "HmacSHA256"))
            doFinal(info + byteArrayOf(1)).copyOf(32)
        }
    }

    private fun keyName(spaceId: String, version: Int): String =
        "space.${MessageDigest.getInstance("SHA-256").digest(spaceId.toByteArray()).b64()}.$version"

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.b64decode(): ByteArray = Base64.decode(this, Base64.DEFAULT)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
