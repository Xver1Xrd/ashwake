package dev.ashwake.ui.settings.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.ColorPicker
import dev.ashwake.ui.components.ColorSwatch
import dev.ashwake.ui.components.FieldLabel
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.SecondaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AccentColor
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.BackgroundStyle
import dev.ashwake.ui.theme.CornerStyle
import dev.ashwake.ui.theme.MAX_CORNER_SCALE
import dev.ashwake.ui.theme.ThemeMode
import dev.ashwake.ui.theme.ThemeSettings
import dev.ashwake.ui.theme.UiDensity
import kotlin.math.roundToInt

/**
 * Редактор темы.
 *
 * Сверху — живой образец: карточка, строка списка и кнопка, собранные из тех
 * же компонентов, что и всё приложение. Настройка без немедленного показа
 * результата превращается в угадайку, а здесь видно и цвет, и радиус, и
 * плотность сразу, не выходя с экрана.
 */
@Composable
fun ThemeEditorScreen(
    onBack: () -> Unit,
    viewModel: ThemeEditorViewModel = hiltViewModel()
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val colors = AshTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AshNavBar(
            title = "Оформление",
            onBack = onBack,
            actions = {
                if (theme.isCustomized) {
                    TextAction(text = "Сброс", onClick = viewModel::reset)
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Preview()

            Section("Тема") {
                ChipRow {
                    ThemeMode.entries.forEach { mode ->
                        ChipButton(
                            text = mode.title,
                            selected = theme.mode == mode,
                            onClick = { viewModel.update { it.copy(mode = mode) } }
                        )
                    }
                }
            }

            AccentSection(theme, viewModel)

            Section(
                title = "Фон",
                footer = theme.background.note
            ) {
                ChipRow {
                    BackgroundStyle.entries.forEach { style ->
                        ChipButton(
                            text = style.title,
                            selected = theme.background == style,
                            onClick = { viewModel.update { it.copy(background = style) } }
                        )
                    }
                }
            }

            SemanticColorsSection(theme, viewModel)

            Section(
                title = "Форма углов",
                footer = when (theme.corner) {
                    CornerStyle.CONTINUOUS -> "Кривизна нарастает плавно, как в iOS"
                    CornerStyle.ROUNDED -> "Обычная дуга окружности"
                    CornerStyle.SHARP -> "Совсем без скруглений"
                }
            ) {
                ChipRow {
                    CornerStyle.entries.forEach { style ->
                        ChipButton(
                            text = style.title,
                            selected = theme.corner == style,
                            onClick = { viewModel.update { it.copy(corner = style) } }
                        )
                    }
                }
            }

            Section(
                title = "Скругление · ${(theme.cornerScale * 100).roundToInt()}%",
                footer = "Множитель всех радиусов сразу: карточек, полей, листов"
            ) {
                ValueSlider(
                    value = theme.cornerScale / MAX_CORNER_SCALE,
                    enabled = theme.corner != CornerStyle.SHARP,
                    onChange = { fraction ->
                        viewModel.update {
                            it.copy(cornerScale = (fraction * MAX_CORNER_SCALE).roundTo(0.05f))
                        }
                    }
                )
            }

            Section(
                title = "Плотность списков",
                footer = "Высота строки и воздух между группами"
            ) {
                ChipRow {
                    UiDensity.entries.forEach { density ->
                        ChipButton(
                            text = density.title,
                            selected = theme.density == density,
                            onClick = { viewModel.update { it.copy(density = density) } }
                        )
                    }
                }
            }

            Section(
                title = "Эффекты",
                footer = "Размытие работает с Android 12; ниже панели просто заливаются"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        title = "Размытие под панелями",
                        checked = theme.blur,
                        onToggle = { viewModel.update { it.copy(blur = !it.blur) } }
                    )
                    ToggleRow(
                        title = "Градиент акцента",
                        checked = theme.gradient,
                        onToggle = { viewModel.update { it.copy(gradient = !it.gradient) } }
                    )
                }
            }

            SecondaryButton(
                text = "Вернуть исходное оформление",
                textColor = colors.danger,
                onClick = viewModel::reset
            )

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ---------------------------------------------------------------------------
// Разделы
// ---------------------------------------------------------------------------

@Composable
private fun AccentSection(theme: ThemeSettings, viewModel: ThemeEditorViewModel) {
    val colors = AshTheme.colors
    var showPicker by remember { mutableStateOf(theme.customAccent != null) }

    Section(
        title = "Акцент",
        footer = "Цвет кнопок, отметок и активной вкладки"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccentColor.entries.forEach { accent ->
                    ColorSwatch(
                        color = accent.resolve(colors.isDark),
                        secondColor = accent.resolveAlt(colors.isDark),
                        selected = theme.customAccent == null && theme.accent == accent,
                        onClick = {
                            showPicker = false
                            viewModel.update { it.copy(accent = accent, customAccent = null) }
                        }
                    )
                }
                // Свой цвет: тот же кружок, но с пипеткой вместо заливки
                Box(
                    Modifier
                        .size(44.dp)
                        .background(colors.surface2, CircleShape)
                        .border(
                            width = if (theme.customAccent != null) 2.5.dp else 0.dp,
                            color = if (theme.customAccent != null) colors.text else Color.Transparent,
                            shape = CircleShape
                        )
                        .tappable(onClick = { showPicker = !showPicker }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AshIcons.Edit,
                        contentDescription = "Свой цвет",
                        tint = colors.text2,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showPicker) {
                ColorPicker(
                    color = theme.customAccent?.let(::Color) ?: colors.accent,
                    onColorChange = { picked ->
                        viewModel.update { it.copy(customAccent = picked.toArgb()) }
                    }
                )
            }
        }
    }
}

/**
 * Семантические цвета.
 *
 * Их четыре, и у каждого своя роль: тёплый — награда и выполнено, холодный —
 * счётчики и таймеры, тревожный — срыв и удаление, зелёный — подтверждение.
 * Менять их по одному имеет смысл: человек, который не различает красный и
 * зелёный, поменяет ровно те два, которые ему мешают.
 */
@Composable
private fun SemanticColorsSection(theme: ThemeSettings, viewModel: ThemeEditorViewModel) {
    val colors = AshTheme.colors
    var editing by remember { mutableStateOf<SemanticRole?>(null) }

    Section(
        title = "Цвета состояний",
        footer = "Награда, счётчики, срыв и подтверждение"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SemanticRole.entries.forEach { role ->
                    val current = role.current(theme, colors)
                    Column(
                        Modifier
                            .weight(1f)
                            .background(colors.surface1, AshShapes.group)
                            .tappable(
                                onClick = { editing = if (editing == role) null else role }
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(current, CircleShape)
                                .border(
                                    width = if (editing == role) 2.dp else 0.dp,
                                    color = if (editing == role) colors.text else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = role.title,
                            style = AshTheme.type.caption,
                            color = colors.text2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            editing?.let { role ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorPicker(
                        color = role.current(theme, colors),
                        onColorChange = { picked ->
                            viewModel.update { role.apply(it, picked.toArgb()) }
                        }
                    )
                    TextAction(
                        text = "Вернуть исходный",
                        onClick = { viewModel.update { role.apply(it, null) } }
                    )
                }
            }
        }
    }
}

/** Роль семантического цвета: заголовок плюс доступ к настройке. */
private enum class SemanticRole(val title: String) {
    WARM("Награда"),
    COLD("Счётчик"),
    DANGER("Срыв"),
    SUCCESS("Готово");

    fun current(theme: ThemeSettings, colors: dev.ashwake.ui.theme.AshColors): Color = when (this) {
        WARM -> theme.warm?.let(::Color) ?: colors.warm
        COLD -> theme.cold?.let(::Color) ?: colors.cold
        DANGER -> theme.danger?.let(::Color) ?: colors.danger
        SUCCESS -> theme.success?.let(::Color) ?: colors.success
    }

    fun apply(theme: ThemeSettings, argb: Int?): ThemeSettings = when (this) {
        WARM -> theme.copy(warm = argb)
        COLD -> theme.copy(cold = argb)
        DANGER -> theme.copy(danger = argb)
        SUCCESS -> theme.copy(success = argb)
    }
}

/**
 * Живой образец. Собран из настоящих компонентов, а не нарисован отдельно:
 * образец, который врёт, хуже, чем его отсутствие.
 */
@Composable
private fun Preview() {
    val colors = AshTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.22f),
                        colors.accentAlt.copy(alpha = 0.10f),
                        colors.surface1
                    )
                ),
                AshShapes.sheet
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Так это выглядит", style = AshTheme.type.title3, color = colors.text)

        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface1, AshShapes.card)
                .padding(horizontal = 12.dp, vertical = AshTheme.density.rowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(colors.danger.copy(alpha = 0.16f), AshShapes.small),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(10.dp).background(colors.danger, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text("Задача с меткой", style = AshTheme.type.headline, color = colors.text)
                Text("срочно · 30 мин", style = AshTheme.type.footnote, color = colors.text2)
            }
            Icon(
                AshIcons.Check,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(18.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(colors.accentGradient, AshShapes.pill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Кнопка",
                    style = AshTheme.type.body,
                    color = if (colors.isDark) Color.Black else Color.White
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(colors.surface2, AshShapes.pill),
                contentAlignment = Alignment.Center
            ) {
                Text("Вторая", style = AshTheme.type.body, color = colors.accent)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Мелочи
// ---------------------------------------------------------------------------

@Composable
private fun Section(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(title)
        content()
        footer?.let {
            Text(
                text = it,
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = AshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, AshShapes.group)
            .tappable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AshTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(46.dp)
                .height(28.dp)
                .background(if (checked) colors.accent else colors.surface3, AshShapes.pill)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(
                        if (checked && colors.isDark) Color.Black
                        else if (checked) Color.White
                        else colors.text2,
                        CircleShape
                    )
            )
        }
    }
}

/** Простой ползунок значения от 0 до 1 на дорожке акцента. */
@Composable
private fun ValueSlider(value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val colors = AshTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .then(if (enabled) Modifier.trackInput(onChange) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(colors.surface3, AshShapes.pill)
        )
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(8.dp)
                .background(
                    if (enabled) colors.accent else colors.text3,
                    AshShapes.pill
                )
        )
    }
}

/**
 * Тап и перетаскивание по дорожке. Вынесено отдельно, потому что модификатор
 * с двумя жестами внутри `then` читается хуже, чем именованная функция.
 */
private fun Modifier.trackInput(onChange: (Float) -> Unit): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures { offset -> onChange((offset.x / size.width).coerceIn(0f, 1f)) }
    }
    .pointerInput(Unit) {
        detectDragGestures { change, _ ->
            onChange((change.position.x / size.width).coerceIn(0f, 1f))
        }
    }

private fun Float.roundTo(step: Float): Float = (this / step).roundToInt() * step
