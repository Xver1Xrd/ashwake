package dev.ashwake.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveCryptoTest {

    private val crypto = ArchiveCrypto()
    private val content = """{"tasks":[{"title":"купить молоко"}]}""".toByteArray()
    private val password = "правильный-пароль".toCharArray()

    @Test
    fun `зашифрованное расшифровывается тем же паролем`() {
        val archive = crypto.encrypt(content, password)
        val result = crypto.decrypt(archive, password.copyOf())

        assertTrue(result is DecryptResult.Success)
        assertArrayEquals(content, (result as DecryptResult.Success).content)
    }

    @Test
    fun `неверный пароль не расшифровывает`() {
        val archive = crypto.encrypt(content, password)
        val result = crypto.decrypt(archive, "другой".toCharArray())

        assertEquals(DecryptResult.WrongPassword, result)
    }

    @Test
    fun `подменённый байт ломает расшифровку, а не даёт мусор`() {
        // Ради этого и выбран GCM: искажённые данные не должны молча
        // превратиться в «успешно расшифрованный» мусор
        val archive = crypto.encrypt(content, password)
        archive[archive.size - 5] = (archive[archive.size - 5] + 1).toByte()

        assertEquals(DecryptResult.WrongPassword, crypto.decrypt(archive, password.copyOf()))
    }

    @Test
    fun `подменённая соль ломает расшифровку`() {
        val archive = crypto.encrypt(content, password)
        val saltOffset = ArchiveCrypto.MAGIC.size + 1
        archive[saltOffset] = (archive[saltOffset] + 1).toByte()

        assertEquals(DecryptResult.WrongPassword, crypto.decrypt(archive, password.copyOf()))
    }

    @Test
    fun `два шифрования одного текста дают разные архивы`() {
        // Случайные соль и nonce: одинаковый шифротекст выдавал бы,
        // что содержимое не менялось
        val first = crypto.encrypt(content, password)
        val second = crypto.encrypt(content, password.copyOf())

        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `незашифрованный архив распознаётся и не выдаётся за поломку`() {
        val plain = content.copyOf()
        assertFalse(crypto.isEncrypted(plain))
        assertEquals(DecryptResult.NotEncrypted, crypto.decrypt(plain, password))
    }

    @Test
    fun `зашифрованный архив помечен сигнатурой`() {
        val archive = crypto.encrypt(content, password)
        assertTrue(crypto.isEncrypted(archive))
        assertArrayEquals(
            ArchiveCrypto.MAGIC,
            archive.copyOfRange(0, ArchiveCrypto.MAGIC.size)
        )
    }

    @Test
    fun `обрезанный архив опознаётся как повреждённый`() {
        val archive = crypto.encrypt(content, password)
        val truncated = archive.copyOfRange(0, ArchiveCrypto.HEADER_SIZE + 2)

        assertTrue(crypto.decrypt(truncated, password.copyOf()) is DecryptResult.Corrupted)
    }

    @Test
    fun `чужая версия формата не расшифровывается вслепую`() {
        val archive = crypto.encrypt(content, password)
        archive[ArchiveCrypto.MAGIC.size] = 99

        val result = crypto.decrypt(archive, password.copyOf())
        assertTrue(result is DecryptResult.Corrupted)
    }

    @Test
    fun `пустое содержимое шифруется и восстанавливается`() {
        val archive = crypto.encrypt(ByteArray(0), password)
        val result = crypto.decrypt(archive, password.copyOf())

        assertTrue(result is DecryptResult.Success)
        assertEquals(0, (result as DecryptResult.Success).content.size)
    }

    @Test
    fun `заголовок фиксированной длины`() {
        val archive = crypto.encrypt(content, password)
        // 4 сигнатура + 1 версия + 16 соль + 12 nonce
        assertEquals(33, ArchiveCrypto.HEADER_SIZE)
        assertTrue(archive.size > ArchiveCrypto.HEADER_SIZE)
    }

    @Test
    fun `пустой пароль это тоже пароль`() {
        // Пустая строка — не «шифрование выключено»: архив ей шифруется,
        // ей же открывается, и чужой пароль к нему не подходит
        val archive = crypto.encrypt("данные".toByteArray(), CharArray(0))

        assertTrue(crypto.decrypt(archive, CharArray(0)) is DecryptResult.Success)
        assertEquals(DecryptResult.WrongPassword, crypto.decrypt(archive, password))
    }
}
