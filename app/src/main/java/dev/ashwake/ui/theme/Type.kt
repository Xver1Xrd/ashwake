package dev.ashwake.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.ashwake.R

/**
 * Типографика из дизайн-системы, раздел 3.
 *
 * Inter — весь интерфейс: ближайший к SF Pro шрифт с полной кириллицей.
 * Onest — только крупные числа, роль как у SF Pro Rounded.
 *
 * Оба шрифта вариативные, поэтому начертания берутся с оси `wght`
 * одного файла, а не отдельными ttf на каждый вес: так вместо восьми
 * файлов в apk лежит один, и синтетического жирного не возникает.
 *
 * Размеры в sp: системная настройка размера шрифта масштабирует всю шкалу,
 * это требование раздела 9.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val InterFamily = FontFamily(
    variableFont(R.font.inter_variable, FontWeight.Normal),
    variableFont(R.font.inter_variable, FontWeight.Medium),
    variableFont(R.font.inter_variable, FontWeight.SemiBold),
    variableFont(R.font.inter_variable, FontWeight.Bold)
)

val OnestFamily = FontFamily(
    variableFont(R.font.onest_variable, FontWeight.Medium),
    variableFont(R.font.onest_variable, FontWeight.Bold)
)

/**
 * Табличные цифры. Без них любой тикающий счётчик дёргается по ширине
 * на каждой смене цифры, а колонки значений в списке не выравниваются.
 */
private const val TABULAR = "tnum"

private fun inter(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = TABULAR
)

/** Шкала iOS Text Styles — именно она даёт узнаваемое ощущение. */
@Immutable
data class AshTypography(
    /** Заголовок экрана в развёрнутом состоянии. */
    val largeTitle: TextStyle = inter(34, 41, FontWeight.Bold),
    /** Заголовки крупных секций. */
    val title1: TextStyle = inter(28, 34, FontWeight.Bold),
    /** Заголовки карточек. */
    val title2: TextStyle = inter(22, 28, FontWeight.Bold),
    /** Подзаголовки. */
    val title3: TextStyle = inter(20, 25, FontWeight.SemiBold),
    /** Название задачи и привычки в строке. */
    val headline: TextStyle = inter(17, 22, FontWeight.SemiBold),
    /** Основной текст, кнопки. */
    val body: TextStyle = inter(17, 22),
    /** Вторичный текст. */
    val callout: TextStyle = inter(16, 21),
    /** Метаданные строки. */
    val subhead: TextStyle = inter(15, 20),
    /** Подписи под числами. */
    val footnote: TextStyle = inter(13, 18),
    /** Заголовки групп списка. Единственное место, где есть капс. */
    val caption: TextStyle = inter(12, 16),
    /** Счётчик отказа. */
    val counter: TextStyle = TextStyle(
        fontFamily = OnestFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 68.sp,
        lineHeight = 68.sp,
        fontFeatureSettings = TABULAR
    ),
    /** Число поменьше: таймер рутины, счётчик в виджете. */
    val counterSmall: TextStyle = TextStyle(
        fontFamily = OnestFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = TABULAR
    )
)

/**
 * Material-шкала поверх той же самой. Нужна ровно затем, чтобы экраны,
 * написанные на `MaterialTheme.typography`, получали Inter и правильные
 * размеры, а не дефолт Roboto: иначе в приложении жили бы две типографики.
 */
fun materialTypography(ash: AshTypography): Typography = Typography(
    displayLarge = ash.largeTitle,
    displayMedium = ash.title1,
    displaySmall = ash.title2,
    headlineLarge = ash.title1,
    headlineMedium = ash.title2,
    headlineSmall = ash.title3,
    titleLarge = ash.title2,
    titleMedium = ash.headline,
    titleSmall = ash.title3,
    bodyLarge = ash.body,
    bodyMedium = ash.callout,
    bodySmall = ash.subhead,
    labelLarge = ash.body.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = ash.footnote,
    labelSmall = ash.caption
)

/** Оставлено для виджетов Glance: там своя типографика, но цифры те же. */
val CounterLarge: TextStyle = AshTypography().counter
val CounterSmall: TextStyle = AshTypography().counterSmall
