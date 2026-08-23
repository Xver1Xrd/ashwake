package dev.ashwake.data.assets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.ashwake.domain.engine.character.ItemBudgetValidator
import dev.ashwake.domain.model.character.EquipSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Каталог из assets прогоняется валидатором целиком (п. 13-Б приёмки).
 *
 * Этот тест появился по конкретному поводу: у эпических наплечников эффекты
 * стоили 69 очков при бюджете 55. Загрузчик на такой записи бросал
 * исключение, каталог читается при открытии главного экрана — и приложение
 * падало сразу после запуска. Опечатку в данных должна ловить сборка,
 * а не человек с телефоном.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CatalogValidationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val loader = CatalogLoader(context, ItemBudgetValidator())

    @Test
    fun `ни один предмет не превышает бюджет своей редкости`() = runTest {
        val validator = ItemBudgetValidator()
        val catalog = loader.load()
        assertTrue("каталог не прочитался", catalog.items.isNotEmpty())

        val result = validator.validate(catalog.items)
        assertTrue(
            "каталог не проходит валидацию:\n" + result.issues.joinToString("\n"),
            result.issues.isEmpty()
        )
    }

    @Test
    fun `каталог собирается и раскрывается палитрами`() = runTest {
        val catalog = loader.load()

        // Один спрайт на диске даёт несколько записей в каталоге (п. 13-А)
        assertTrue("палитры не раскрылись", catalog.items.size > 200)
        assertTrue("предметы не индексируются по id", catalog.itemsById.isNotEmpty())
    }

    @Test
    fun `у каждого пользовательского слота есть хотя бы один предмет`() = runTest {
        val catalog = loader.load()
        val filled = catalog.items.map { it.slot }.toSet()

        val missing = EquipSlot.entries.filter { it.isUserFacing && it !in filled }
        assertTrue("пустые слоты: $missing", missing.isEmpty())
    }
}
