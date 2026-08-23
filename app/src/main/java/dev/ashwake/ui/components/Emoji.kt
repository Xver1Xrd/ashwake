package dev.ashwake.ui.components

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * Значок задачи.
 *
 * Эмодзи вместо своей иконотеки — сознательный выбор: набор рисуется
 * системой, у него есть все категории жизни разом, и человеку не нужно
 * искать «подходящую иконку» среди двадцати похожих. Хранится одна строка,
 * рисуется обычным текстом.
 */

/**
 * Готовый набор для выбора. Не «все эмодзи», а те, что реально ложатся
 * на задачи: полный системный список — это отдельный экран поиска,
 * в котором выбор занимает больше времени, чем сама задача.
 */
val TaskEmojiPresets: List<String> = listOf(
    "📌", "⭐", "🔥", "⚡", "🎯", "✅", "🧠", "💡",
    "💼", "💻", "📞", "✉️", "📅", "📝", "📚", "🎓",
    "🏋️", "🏃", "🧘", "🚴", "🥗", "💊", "😴", "🚿",
    "🏠", "🧹", "🧺", "🛒", "🍳", "🐾", "🌱", "🔧",
    "💰", "🏦", "🧾", "🎁", "✈️", "🚗", "🎬", "🎧",
    "👨‍👩‍👧", "❤️", "🎉", "🍰", "☕", "🌙", "🎮", "🧩"
)

/**
 * Значок в строке списка: эмодзи на скруглённой подложке.
 * Размер задаётся снаружи, потому что в строке дня и в карточке матрицы
 * он разный, а рисуется одинаково.
 */
@Composable
fun EmojiBadge(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    background: Color = AshTheme.colors.surface2
) {
    Box(
        modifier
            .size(size)
            .background(background, AshShapes.squircle(size / 3)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            // Эмодзи рисуется шрифтом системы, поэтому размер задаётся здесь,
            // а не берётся из шкалы: в шкале нет стиля «картинка»
            fontSize = (size.value * 0.52f).sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Выбор значка: сетка готовых эмодзи плюс кнопка «без значка».
 *
 * Сетка выложена рядами вручную, а не `LazyVerticalGrid`: она живёт внутри
 * вертикально прокручиваемой формы, где вложенная ленивая сетка требует
 * фиксированной высоты и перехватывает прокрутку.
 */
@Composable
fun EmojiPicker(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 8
) {
    val colors = AshTheme.colors

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, AshShapes.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TaskEmojiPresets.chunked(columns).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { emoji ->
                    val isSelected = emoji == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) colors.accent.copy(alpha = 0.20f) else Color.Transparent,
                                AshShapes.small
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) colors.accent else Color.Transparent,
                                shape = AshShapes.small
                            )
                            .tappable(onClick = { onSelect(emoji) })
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
                // Последний ряд добивается пустотой, иначе значки в нём
                // растянутся шире остальных
                repeat(columns - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }

        if (selected != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .tappable(onClick = { onSelect(null) })
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    AshIcons.Close,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(16.dp)
                )
                Text(stringResource(R.string.components_ubrat_znachok), style = AshTheme.type.subhead, color = colors.text2)
            }
        }
    }
}
