package dev.ashwake.ui.ritual

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.hasMark
import dev.ashwake.ui.theme.priorityColor
import dev.ashwake.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Интерактивный стек карточек в стиле Tinder для быстрого разбора хвостов:
 * Свайп вправо → «Сделано», свайп влево → «На завтра».
 */
@Composable
fun TaskSwipeStack(
    tasks: List<Task>,
    onComplete: (Task) -> Unit,
    onPostpone: (Task) -> Unit,
    onPostponeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var currentIndex by remember(tasks.map { it.id }) { mutableIntStateOf(0) }
    val remainingTasks = tasks.drop(currentIndex)

    if (remainingTasks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎉 Все задачи разобраны!",
                    style = AshTheme.type.title3,
                    color = colors.success
                )
                Text(
                    text = "Отличная работа, хвостов на сегодня не осталось.",
                    style = AshTheme.type.footnote,
                    color = colors.text2
                )
            }
        }
        return
    }

    val currentTask = remainingTasks.first()
    val swipeOffset = remember(currentTask.id) { Animatable(0f) }
    val thresholdPx = with(density) { 110.dp.toPx() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Осталось разобрать: ${remainingTasks.size}",
                style = AshTheme.type.caption,
                color = colors.text2
            )
            if (remainingTasks.size > 1) {
                Text(
                    text = "Перенести все на завтра",
                    style = AshTheme.type.caption,
                    color = colors.accent,
                    modifier = Modifier.tappable { onPostponeAll() }
                )
            }
        }

        // Контейнер карточек (стек)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // Подложка 2-го уровня
            if (remainingTasks.size > 1) {
                val next = remainingTasks[1]
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(150.dp)
                        .offset(y = 12.dp)
                        .scale(0.95f)
                        .clip(AshShapes.card)
                        .background(colors.surface2)
                        .border(1.dp, colors.surface3, AshShapes.card)
                        .padding(16.dp)
                ) {
                    Text(
                        text = next.title,
                        style = AshTheme.type.body,
                        color = colors.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Верхняя активная карточка
            val dragX = swipeOffset.value
            val rotation = (dragX / 25f).coerceIn(-20f, 20f)
            val completeAlpha = (dragX / thresholdPx).coerceIn(0f, 1f)
            val postponeAlpha = (-dragX / thresholdPx).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .offset { IntOffset(dragX.roundToInt(), 0) }
                    .graphicsLayer { rotationZ = rotation }
                    .clip(AshShapes.card)
                    .background(colors.surface1)
                    .border(
                        width = 1.5.dp,
                        color = when {
                            completeAlpha > 0.1f -> colors.success.copy(alpha = completeAlpha)
                            postponeAlpha > 0.1f -> colors.cold.copy(alpha = postponeAlpha)
                            else -> colors.surface3
                        },
                        shape = AshShapes.card
                    )
                    .pointerInput(currentTask.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset.value > thresholdPx) {
                                    scope.launch {
                                        haptics.play(HapticKind.TASK_COMPLETE)
                                        swipeOffset.animateTo(800f, spring(stiffness = Spring.StiffnessMedium))
                                        onComplete(currentTask)
                                        currentIndex++
                                    }
                                } else if (swipeOffset.value < -thresholdPx) {
                                    scope.launch {
                                        haptics.play(HapticKind.LIGHT)
                                        swipeOffset.animateTo(-800f, spring(stiffness = Spring.StiffnessMedium))
                                        onPostpone(currentTask)
                                        currentIndex++
                                    }
                                } else {
                                    scope.launch {
                                        swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { swipeOffset.animateTo(0f) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    swipeOffset.snapTo(swipeOffset.value + dragAmount)
                                }
                            }
                        )
                    }
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentTask.priority.hasMark) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.priorityColor(currentTask.priority))
                            )
                        } else {
                            Spacer(Modifier.size(8.dp))
                        }

                        Text(
                            text = if (currentTask.estimateMinutes != null) "~${currentTask.estimateMinutes}м" else "",
                            style = AshTheme.type.caption,
                            color = colors.text2
                        )
                    }

                    Text(
                        text = currentTask.title,
                        style = AshTheme.type.title3,
                        color = colors.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (currentTask.subtasks.isNotEmpty()) {
                        Text(
                            text = "Подзадачи: ${currentTask.subtasks.count { it.isDone }}/${currentTask.subtasks.size}",
                            style = AshTheme.type.caption,
                            color = colors.text2
                        )
                    }
                }

                // Маркеры свайпа
                if (completeAlpha > 0.05f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .border(2.dp, colors.success.copy(alpha = completeAlpha), AshShapes.pill)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "СДЕЛАНО",
                            style = AshTheme.type.caption,
                            fontWeight = FontWeight.Bold,
                            color = colors.success.copy(alpha = completeAlpha)
                        )
                    }
                }

                if (postponeAlpha > 0.05f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .border(2.dp, colors.cold.copy(alpha = postponeAlpha), AshShapes.pill)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "НА ЗАВТРА",
                            style = AshTheme.type.caption,
                            fontWeight = FontWeight.Bold,
                            color = colors.cold.copy(alpha = postponeAlpha)
                        )
                    }
                }
            }
        }

        // Кнопки быстрых действий под карточкой
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка: На завтра
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colors.surface2)
                        .border(1.5.dp, colors.cold.copy(alpha = 0.5f), CircleShape)
                        .tappable {
                            scope.launch {
                                haptics.play(HapticKind.LIGHT)
                                swipeOffset.animateTo(-800f, spring(stiffness = Spring.StiffnessMedium))
                                onPostpone(currentTask)
                                currentIndex++
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AshIcons.Calendar,
                        contentDescription = "На завтра",
                        tint = colors.cold,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("На завтра", style = AshTheme.type.caption, color = colors.cold)
            }

            // Кнопка: Сделано
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colors.surface2)
                        .border(1.5.dp, colors.success.copy(alpha = 0.5f), CircleShape)
                        .tappable {
                            scope.launch {
                                haptics.play(HapticKind.TASK_COMPLETE)
                                swipeOffset.animateTo(800f, spring(stiffness = Spring.StiffnessMedium))
                                onComplete(currentTask)
                                currentIndex++
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AshIcons.Check,
                        contentDescription = "Сделано",
                        tint = colors.success,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Сделано", style = AshTheme.type.caption, color = colors.success)
            }
        }
    }
}
