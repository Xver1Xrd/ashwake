package dev.ashwake.online.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentity @Inject constructor(private val vault: SecureVault) {

    fun ensureCreated() {
        signingPair()
        agreementPair()
    }

    fun signingPublicPem(): String = signingPair().public.toPem()

    fun encryptionPublicPem(): String = agreementPair().public.toPem()

    fun sign(value: String): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(signingPair().private)
        signature.update(value.toByteArray())
        return Base64.encodeToString(signature.sign(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun agree(peerPublicKey: PublicKey): ByteArray = KeyAgreement.getInstance("ECDH").run {
        init(agreementPair().private)
        doPhase(peerPublicKey, true)
        generateSecret()
    }

    fun decodePublic(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    private fun signingPair(): KeyPair {
        keyStorePair(SIGN_ALIAS)?.let { return it }
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(
                    SIGN_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKeyPair()
        }
    }

    private fun agreementPair(): KeyPair {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            keyStorePair(AGREEMENT_ALIAS)?.let { return it }
            return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
                initialize(
                    KeyGenParameterSpec.Builder(AGREEMENT_ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
                generateKeyPair()
            }
        }

        val privateBytes = vault.get(SOFTWARE_PRIVATE)
        val publicBytes = vault.get(SOFTWARE_PUBLIC)
        if (privateBytes != null && publicBytes != null) {
            val factory = KeyFactory.getInstance("EC")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(publicBytes)),
                factory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
            )
        }
        return KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec(CURVE))
            generateKeyPair().also {
                vault.put(SOFTWARE_PRIVATE, it.private.encoded)
                vault.put(SOFTWARE_PUBLIC, it.public.encoded)
            }
        }
    }

    private fun keyStorePair(alias: String): KeyPair? {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = store.getKey(alias, null) as? PrivateKey ?: return null
        val publicKey = store.getCertificate(alias)?.publicKey ?: return null
        return KeyPair(publicKey, privateKey)
    }

    private fun PublicKey.toPem(): String {
        val body = Base64.encodeToString(encoded, Base64.NO_WRAP)
            .chunked(64)
            .joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val SIGN_ALIAS = "ashwake.online.signing.v1"
        const val AGREEMENT_ALIAS = "ashwake.online.agreement.v1"
        const val SOFTWARE_PRIVATE = "device.agreement.private"
        const val SOFTWARE_PUBLIC = "device.agreement.public"
        const val CURVE = "secp256r1"
    }
}
