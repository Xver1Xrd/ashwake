package dev.ashwake.data.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Результат расшифровки: пароль либо подошёл, либо нет. Третьего не дано. */
sealed interface DecryptResult {
    data class Success(val content: ByteArray) : DecryptResult {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    data object WrongPassword : DecryptResult
    data object NotEncrypted : DecryptResult
    data class Corrupted(val reason: String) : DecryptResult
}

/**
 * Шифрование архива резервной копии (п. 13).
 *
 * Формат: `ASHW` | версия | соль (16) | nonce (12) | шифротекст с тегом.
 * Соль и nonce случайные и лежат в заголовке открытым текстом — так и надо:
 * секретен только пароль, а без соли в заголовке расшифровать свой же архив
 * было бы нечем.
 *
 * Ключ выводится PBKDF2-HMAC-SHA256, содержимое шифруется AES-256-GCM.
 * GCM выбран потому, что он сам проверяет целостность: подменённый байт
 * даёт ошибку тега, а не мусор, который импорт принял бы за данные.
 *
 * **Пароль нигде не хранится.** Он спрашивается при восстановлении,
 * и забытый пароль означает потерянный архив — это цена того,
 * что копию можно спокойно класть в любую синхронизируемую папку.
 */
@Singleton
class ArchiveCrypto @Inject constructor() {

    fun encrypt(content: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        val encrypted = cipher.doFinal(content)

        return MAGIC + byteArrayOf(VERSION) + salt + nonce + encrypted
    }

    fun decrypt(archive: ByteArray, password: CharArray): DecryptResult {
        if (!isEncrypted(archive)) return DecryptResult.NotEncrypted
        if (archive.size < HEADER_SIZE + TAG_BITS / 8) {
            return DecryptResult.Corrupted("Архив короче заголовка")
        }
        if (archive[MAGIC.size] != VERSION) {
            return DecryptResult.Corrupted("Неизвестная версия формата")
        }

        val salt = archive.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_SIZE)
        val nonce = archive.copyOfRange(MAGIC.size + 1 + SALT_SIZE, HEADER_SIZE)
        val payload = archive.copyOfRange(HEADER_SIZE, archive.size)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt),
                GCMParameterSpec(TAG_BITS, nonce)
            )
            DecryptResult.Success(cipher.doFinal(payload))
        } catch (error: AEADBadTagException) {
            // Неверный пароль и подменённые данные неотличимы — и это правильно:
            // подсказывать, что «пароль верный, но файл битый», значит помогать перебору
            DecryptResult.WrongPassword
        } catch (error: Exception) {
            DecryptResult.Corrupted(error.message ?: "Не удалось расшифровать")
        }
    }

    /** Зашифрован ли архив: по сигнатуре в начале файла. */
    fun isEncrypted(archive: ByteArray): Boolean =
        archive.size >= MAGIC.size && archive.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val key = factory.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }

    companion object {
        /** Сигнатура файла: по ней видно, зашифрован архив или это обычный JSON. */
        val MAGIC = "ASHW".toByteArray(Charsets.US_ASCII)
        const val VERSION: Byte = 1

        const val SALT_SIZE = 16
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256

        /**
         * Число итераций PBKDF2. Чем больше, тем дороже перебор;
         * порядок соответствует современным рекомендациям и на телефоне
         * занимает доли секунды — а бэкап делается раз в сутки.
         */
        const val ITERATIONS = 210_000

        const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        val HEADER_SIZE = MAGIC.size + 1 + SALT_SIZE + NONCE_SIZE
    }
}
