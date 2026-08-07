package dev.ashwake.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Типографика по умолчанию Material 3 с одной правкой: счётчики
 * (дни отказа, число дней в виджете) требуют моноширинных цифр,
 * иначе тикающий счётчик дёргается по ширине каждую секунду.
 */
val AshwakeTypography = Typography()

val CounterLarge = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 56.sp
)

val CounterSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp
)
