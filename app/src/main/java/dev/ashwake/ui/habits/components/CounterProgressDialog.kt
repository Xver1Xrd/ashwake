package dev.ashwake.ui.habits.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ashwake.R
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.SecondaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.rememberHaptics
import kotlin.math.roundToInt

/**
 * Диалог быстрого ввода прогресса для привычек-счётчиков.
 *
 * Позволяет в один клик:
 * - Выполнить цель целиком (например, сразу все 60 мин)
 * - Добавить порцию (+15, +30, +45...)
 * - Ввести точное число с клавиатуры
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CounterProgressDialog(
    progress: HabitWithProgress,
    onSetProgress: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val habit = progress.habit
    val colors = AshTheme.colors
    val haptics = rememberHaptics()
    val target = habit.targetValue
    val current = progress.todayValue
    val unit = habit.unitName?.let { " $it" }.orEmpty()

    val quickSteps = remember(target) { calculateQuickSteps(target) }
    var inputCustom by remember { mutableStateOf("") }

    val remaining = (target - current).coerceAtLeast(0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = habit.name,
                    style = AshTheme.type.title3,
                    color = colors.text
                )
                Text(
                    text = "Сегодня: ${formatValue(current)} / ${formatValue(target)}$unit",
                    style = AshTheme.type.subhead,
                    color = colors.text2
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Полоса текущего прогресса
                val fraction = if (target > 0f) (current / target).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.surface3, AshShapes.pill)
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .background(
                                    if (current >= target) colors.success else colors.accent,
                                    AshShapes.pill
                                )
                        )
                    }
                }

                // Быстрые кнопки порций
                Text(
                    text = "Быстро добавить:",
                    style = AshTheme.type.caption,
                    color = colors.text2
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSteps.forEach { step ->
                        ChipButton(
                            text = "+$step$unit",
                            selected = false,
                            onClick = {
                                haptics.play(HapticKind.LIGHT)
                                onSetProgress(current + step)
                                onDismiss()
                            }
                        )
                    }

                    if (remaining > 0f && quickSteps.none { it.toFloat() == remaining }) {
                        ChipButton(
                            text = "+До конца (${formatValue(remaining)}$unit)",
                            selected = false,
                            onClick = {
                                haptics.play(HapticKind.LIGHT)
                                onSetProgress(target)
                                onDismiss()
                            }
                        )
                    }
                }

                HorizontalDivider(color = colors.separator)

                // Точный ручной ввод
                Text(
                    text = "Или ввести точное число:",
                    style = AshTheme.type.caption,
                    color = colors.text2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AshTextField(
                        value = inputCustom,
                        onValueChange = { inputCustom = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                        placeholder = "Например, ${quickSteps.firstOrNull() ?: 10}",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val added = inputCustom.toFloatOrNull()
                                if (added != null && added > 0f) {
                                    haptics.play(HapticKind.LIGHT)
                                    onSetProgress(current + added)
                                    onDismiss()
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    TextAction(
                        text = "Добавить",
                        onClick = {
                            val added = inputCustom.toFloatOrNull()
                            if (added != null && added > 0f) {
                                haptics.play(HapticKind.LIGHT)
                                onSetProgress(current + added)
                                onDismiss()
                            }
                        }
                    )
                }

                if (current > 0f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextAction(
                            text = "Сбросить на 0",
                            color = colors.danger,
                            onClick = {
                                haptics.play(HapticKind.LIGHT)
                                onSetProgress(0f)
                                onDismiss()
                            }
                        )
                        TextAction(
                            text = "Завершить норму",
                            color = colors.success,
                            onClick = {
                                haptics.play(HapticKind.LIGHT)
                                onSetProgress(target)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (current < target) {
                PrimaryButton(
                    text = "Выполнить полностью (${formatValue(target)}$unit)",
                    onClick = {
                        haptics.play(HapticKind.TASK_COMPLETE)
                        onSetProgress(target)
                        onDismiss()
                    }
                )
            } else {
                SecondaryButton(
                    text = stringResource(R.string.detail_ponyatno),
                    onClick = onDismiss
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_otmena))
            }
        }
    )
}

/** Расчёт удобных порций добавления в зависимости от величины цели */
private fun calculateQuickSteps(target: Float): List<Int> {
    val targetInt = target.roundToInt()
    val steps = linkedSetOf<Int>()

    when {
        targetInt <= 5 -> {
            steps += listOf(1, 2, targetInt).filter { it in 1..targetInt }
        }
        targetInt in 6..15 -> {
            steps += listOf(1, 2, 5, targetInt).filter { it in 1..targetInt }
        }
        targetInt in 16..35 -> {
            steps += listOf(5, 10, 15, targetInt).filter { it in 1..targetInt }
        }
        targetInt in 36..120 -> {
            // Например 60 минут: +15, +30, +45, +60
            val quarter = (targetInt * 0.25f).roundToInt()
            val half = (targetInt * 0.5f).roundToInt()
            val threeQuarter = (targetInt * 0.75f).roundToInt()
            steps += listOf(10, 15, quarter, half, threeQuarter, targetInt)
                .filter { it in 1..targetInt }
                .sorted()
        }
        targetInt in 121..999 -> {
            val quarter = (targetInt * 0.25f).roundToInt()
            val half = (targetInt * 0.5f).roundToInt()
            steps += listOf(25, 50, 100, quarter, half, targetInt)
                .filter { it in 1..targetInt }
                .sorted()
        }
        else -> {
            // Крупные цели (1000, 5000, 10000 шагов и т.д.)
            val tenth = (targetInt * 0.1f).roundToInt()
            val quarter = (targetInt * 0.25f).roundToInt()
            val half = (targetInt * 0.5f).roundToInt()
            steps += listOf(500, 1000, tenth, quarter, half, targetInt)
                .filter { it in 1..targetInt }
                .sorted()
        }
    }

    return steps.toList().take(5)
}
