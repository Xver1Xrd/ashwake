package dev.ashwake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ashwake.R
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * Значок задачи.
 */
val TaskEmojiPresets: List<String> = listOf(
    "📌", "⭐", "🔥", "⚡", "🎯", "✅", "🧠", "💡",
    "💼", "💻", "📞", "✉️", "📅", "📝", "📚", "🎓",
    "🏋️", "🏃", "🧘", "🚴", "🥗", "💊", "😴", "🚿",
    "🏠", "🧹", "🧺", "🛒", "🍳", "🐾", "🌱", "🔧",
    "💰", "🏦", "🧾", "🎁", "✈️", "🚗", "🎬", "🎧",
    "👨‍👩‍👧", "❤️", "🎉", "🍰", "☕", "🌙", "🎮", "🧩"
)

val EmojiCategories: Map<String, List<String>> = linkedMapOf(
    "Частые" to TaskEmojiPresets,
    "Смайлы" to listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😋",
        "😜", "🤪", "😎", "🤩", "🥳", "😏", "🤔", "🤫",
        "😴", "😷", "🤯", "🥶", "🥵", "🥺", "🤠", "🤖"
    ),
    "Работа" to listOf(
        "💼", "💻", "🖥️", "⌨️", "📱", "📞", "✉️", "📧",
        "📝", "📋", "📁", "📂", "📊", "📈", "📉", "📅",
        "📆", "🗓️", "📌", "📎", "📏", "🔬", "🔭", "💡",
        "💰", "💵", "💳", "🧾", "🏦", "🏷️", "📦", "🔨"
    ),
    "Дом и быт" to listOf(
        "🏠", "🏡", "🛋️", "🛏️", "🚪", "🚿", "🛁", "🧹",
        "🧺", "🧽", "🧼", "🧴", "🪥", "🔑", "🗝️", "🛒",
        "🛍️", "🍳", "🥘", "🍲", "🥣", "🥗", "🍕", "🍔",
        "☕", "🍵", "🥤", "🍎", "🍌", "🥑", "🥦", "🍰"
    ),
    "Спорт" to listOf(
        "🏋️", "🏃", "🚶", "🧘", "🚴", "🧗", "🏊", "⚽",
        "🏀", "🏈", "🎾", "🏐", "🥊", "🥋", "🎯", "⛳",
        "🎣", "🎮", "🎲", "🧩", "🎨", "🎬", "🎤", "🎧",
        "🎸", "🎹", "📚", "✈️", "🚗", "🚲", "🏖️", "🏕️"
    ),
    "Природа" to listOf(
        "🐾", "🐶", "🐱", "🐭", "🐰", "🦊", "🐻", "🐼",
        "🦁", "🐯", "🐨", "🐵", "🦄", "🦅", "🦉", "🐸",
        "🐙", "🦋", "🐝", "🌱", "🌿", "🍀", "🎍", "🌴",
        "🌲", "🌳", "🍁", "🌸", "🌻", "🌞", "🌙", "⭐"
    ),
    "Символы" to listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "💔", "❣️", "💕", "✨", "🔥", "⚡", "💥", "⭐",
        "🌟", "💫", "🎯", "✅", "❌", "⚠️", "⛔", "🚫",
        "💯", "🆗", "🆙", "🔔", "🎵", "💬", "💭", "💤"
    )
)

/**
 * Значок в строке списка: эмодзи на скруглённой подложке.
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
            fontSize = (size.value * 0.52f).sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Выбор значка: категории эмодзи + ввод ЛЮБОГО эмодзи с клавиатуры.
 */
@Composable
fun EmojiPicker(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 8
) {
    val colors = AshTheme.colors
    var activeCategory by remember { mutableStateOf("Частые") }
    var customEmojiText by remember { mutableStateOf("") }

    val currentItems = EmojiCategories[activeCategory] ?: TaskEmojiPresets

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, AshShapes.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Поле ввода ЛЮБОГО эмодзи с клавиатуры
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AshTextField(
                value = customEmojiText,
                onValueChange = { input ->
                    customEmojiText = input
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty()) {
                        onSelect(trimmed)
                    }
                },
                placeholder = "Ввести любой эмодзи с клавиатуры ⌨️...",
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (selected != null) {
                IconAction(
                    icon = AshIcons.Close,
                    contentDescription = stringResource(R.string.editor_ubrat),
                    tint = colors.text2,
                    onClick = {
                        customEmojiText = ""
                        onSelect(null)
                    }
                )
            }
        }

        // Выбранный эмодзи, если он не из стандартных или просто активен
        if (selected != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Выбранный значок:",
                    style = AshTheme.type.footnote,
                    color = colors.text2
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(colors.accent.copy(alpha = 0.20f), AshShapes.small)
                        .border(1.5.dp, colors.accent, AshShapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = selected, fontSize = 20.sp)
                }
            }
        }

        // Категории эмодзи
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EmojiCategories.keys.forEach { cat ->
                ChipButton(
                    text = cat,
                    selected = activeCategory == cat,
                    onClick = { activeCategory = cat }
                )
            }
        }

        // Сетка эмодзи текущей категории
        currentItems.chunked(columns).forEach { rowItems ->
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
                            .tappable(onClick = {
                                customEmojiText = ""
                                onSelect(emoji)
                            })
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
                repeat(columns - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
