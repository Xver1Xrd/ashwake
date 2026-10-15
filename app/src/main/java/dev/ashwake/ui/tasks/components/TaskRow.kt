package dev.ashwake.ui.tasks.components

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.rememberHaptics
import androidx.compose.ui.text.style.TextDecoration
import dev.ashwake.ui.components.responseSpring
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import dev.ashwake.domain.model.tasks.StaleLevel
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.components.AshContextMenu
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.ContextMenuItem
import dev.ashwake.ui.components.EntityIcon
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.hasMark
import dev.ashwake.ui.theme.priorityColor
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val RU_LOCALE = Locale("ru")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", RU_LOCALE)
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Строка задачи со свайпами: вправо — выполнено, влево — на завтра (п. 1).
 *
 * `confirmValueChange` намеренно возвращает false: строка отыгрывает свайп и
 * возвращается на место, а действие выполняется отдельно. Иначе перенесённая
 * задача осталась бы «уехавшей» — она ведь никуда из списка не делась.
 */
@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    onComplete: () -> Unit,
    onPostpone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    if (selectionMode) {
        TaskRowContent(
            task = task,
            today = today,
            onClick = { onSelectToggle?.invoke() },
            onComplete = null,
            onPostpone = null,
            expanded = expanded,
            onExpandToggle = onExpandToggle,
            selectionMode = true,
            selected = selected,
            onLongClick = onLongClick,
            onDelete = onDelete,
            modifier = modifier
        )
        return
    }

    val complete by rememberUpdatedState(onComplete)
    val postpone by rememberUpdatedState(onPostpone)

    val dismissStateHolder = remember { arrayOf<SwipeToDismissBoxState?>(null) }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.50f },
        confirmValueChange = { value ->
            val progress = dismissStateHolder[0]?.progress ?: 0f
            if (progress >= 0.50f) {
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> complete()
                    SwipeToDismissBoxValue.EndToStart -> postpone()
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
            false
        }
    )
    dismissStateHolder[0] = dismissState

    val haptics = rememberHaptics()
    val reachedThreshold = dismissState.progress >= 0.50f
    LaunchedEffect(reachedThreshold) {
        if (reachedThreshold && dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
            haptics.play(HapticKind.LIGHT)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            SwipeBackground(
                direction = dismissState.dismissDirection,
                reachedThreshold = reachedThreshold
            )
        },
        content = {
            TaskRowContent(
                task = task,
                today = today,
                onClick = onClick,
                onComplete = complete,
                onPostpone = { postpone() },
                expanded = expanded,
                onExpandToggle = onExpandToggle,
                selectionMode = false,
                selected = false,
                onLongClick = onLongClick,
                onDelete = onDelete
            )
        }
    )
}

@Composable
private fun SwipeBackground(
    direction: SwipeToDismissBoxValue,
    reachedThreshold: Boolean = false
) {
    val colors = AshTheme.colors
    val (color, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            Triple(colors.success, AshIcons.Check, Alignment.CenterStart)
        SwipeToDismissBoxValue.EndToStart ->
            Triple(colors.cold, AshIcons.EventRepeat, Alignment.CenterEnd)
        SwipeToDismissBoxValue.Settled ->
            Triple(Color.Transparent, AshIcons.Check, Alignment.Center)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(AshShapes.card)
            .background(if (reachedThreshold) color else color.copy(alpha = 0.55f))
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (colors.isDark) Color.Black else Color.White,
                modifier = Modifier.graphicsLayer {
                    scaleX = if (reachedThreshold) 1.2f else 0.85f
                    scaleY = if (reachedThreshold) 1.2f else 0.85f
                    alpha = if (reachedThreshold) 1f else 0.65f
                }
            )
        }
    }
}

@Composable
private fun TaskRowContent(
    task: Task,
    today: LocalDate,
    onClick: () -> Unit,
    onComplete: (() -> Unit)? = null,
    onPostpone: (() -> Unit)? = null,
    expanded: Boolean,
    onExpandToggle: (() -> Unit)?,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isCompleting by remember(task.id, task.isDone) { mutableStateOf(false) }
    val isTaskDone = task.isDone || isCompleting
    val scope = rememberCoroutineScope()
    val colors = AshTheme.colors
    val priorityColor = colors.priorityColor(task.priority)

    val cardScale by animateFloatAsState(
        targetValue = if (isTaskDone) 0.98f else 1f,
        animationSpec = responseSpring(),
        label = "task-scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isTaskDone) 0.45f else 1f,
        animationSpec = tween(300),
        label = "task-alpha"
    )
    val strikeProgress by animateFloatAsState(
        targetValue = if (isTaskDone) 1f else 0f,
        animationSpec = tween(300),
        label = "strike-progress"
    )

    fun triggerComplete() {
        if (!task.isDone && !isCompleting && onComplete != null) {
            isCompleting = true
            scope.launch {
                delay(320)
                onComplete()
            }
        } else {
            isCompleting = false
            onComplete?.invoke()
        }
    }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                }
                .clip(AshShapes.card)
                .background(colors.surface1)
                .border(
                    width = if (colors.isDark) 0.dp else 0.75.dp,
                    color = if (colors.isDark) Color.Transparent else colors.surface3,
                    shape = AshShapes.card
                )
                .tappable(
                    onClick = onClick,
                    onLongClick = {
                        if (onLongClick != null) onLongClick() else showMenu = true
                    }
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (selected) colors.accent else Color.Transparent)
                    .border(
                        width = if (selected) 0.dp else 2.dp,
                        color = if (selected) colors.accent else colors.text3,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        AshIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(AshShapes.squircle(13.dp))
                    .background(
                        if (isTaskDone) colors.success.copy(alpha = 0.2f)
                        else if (task.priority.hasMark) priorityColor.copy(alpha = 0.16f)
                        else colors.surface2
                    )
                    .tappable { triggerComplete() },
                contentAlignment = Alignment.Center
            ) {
                if (isTaskDone) {
                    Icon(
                        AshIcons.Check,
                        contentDescription = "Выполнено",
                        tint = colors.success,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (task.emoji != null || task.iconPath != null) {
                    EntityIcon(
                        emoji = task.emoji,
                        iconPath = task.iconPath,
                        size = 30.dp
                    )
                } else {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(
                                if (task.priority.hasMark) priorityColor else colors.text3,
                                AshShapes.pill
                            )
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = task.title,
                style = AshTheme.type.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (task.isDone) colors.text3 else colors.text,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (strikeProgress > 0f) {
                        val y = size.height / 2f
                        drawLine(
                            color = colors.text3,
                            start = Offset(0f, y),
                            end = Offset(size.width * strikeProgress, y),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DifficultyDots(difficulty = task.difficulty)
                val meta = buildMeta(task, today)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = AshTheme.type.footnote,
                        color = if (task.isOverdue(today)) colors.danger else colors.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (task.tags.isNotEmpty()) {
                Text(
                    text = task.tags.joinToString(" ") { "#${it.name}" },
                    style = AshTheme.type.caption,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        MetaIcon(task.recurrence?.let { AshIcons.Repeat })
        MetaIcon(task.sourceLink?.let { AshIcons.Link })
        StaleBadge(task)

        if (task.subtasks.isNotEmpty() && onExpandToggle != null) {
            Box(
                Modifier.size(28.dp).tappable(onClick = onExpandToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (expanded) AshIcons.ExpandLess else AshIcons.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.components_svernut_podzadachi)
                    else stringResource(R.string.components_pokazat_podzadachi),
                    tint = colors.text3,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()

    AshContextMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        items = listOfNotNull(
            ContextMenuItem(
                title = stringResource(R.string.context_menu_edit),
                icon = AshIcons.Edit,
                onClick = onClick
            ),
            ContextMenuItem(
                title = "Скопировать",
                icon = AshIcons.Copy,
                onClick = {
                    val text = buildString {
                        append(task.title)
                        if (task.subtasks.isNotEmpty()) {
                            task.subtasks.forEach { sub ->
                                append("\n- ")
                                append(if (sub.isDone) "[x] " else "[ ] ")
                                append(sub.title)
                            }
                        }
                    }
                    clipboard.setText(AnnotatedString(text))
                    haptics.play(HapticKind.LIGHT)
                }
            ),
            onPostpone?.let {
                ContextMenuItem(
                    title = stringResource(R.string.context_menu_postpone_tomorrow),
                    icon = AshIcons.Calendar,
                    onClick = it
                )
            },
            onDelete?.let {
                ContextMenuItem(
                    title = stringResource(R.string.blocking_udalit),
                    icon = AshIcons.Delete,
                    isDestructive = true,
                    onClick = { showDeleteConfirm = true }
                )
            }
        )
    )

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { androidx.compose.material3.Text("Удалить задачу?") },
            text = { androidx.compose.material3.Text("Задача «${task.title}» будет перемещена в корзину.") },
            confirmButton = {
                dev.ashwake.ui.components.TextAction(
                    text = stringResource(R.string.blocking_udalit),
                    color = AshTheme.colors.danger,
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    }
                )
            },
            dismissButton = {
                dev.ashwake.ui.components.TextAction(
                    text = stringResource(R.string.detail_otmena),
                    onClick = { showDeleteConfirm = false }
                )
            }
        )
    }
    }
}

/** Значок-признак в конце строки: повтор, ссылка. Ничего не делает по нажатию. */
@Composable
private fun MetaIcon(icon: ImageVector?) {
    if (icon == null) return
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = AshTheme.colors.text3
    )
}

/** Подзадачи под строкой. Отдельно от [TaskRow], чтобы свайп не задевал их. */
@Composable
fun SubtaskList(
    task: Task,
    onToggle: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    Column(modifier = modifier.padding(start = 30.dp, top = 2.dp)) {
        task.subtasks.forEach { subtask ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AshShapes.small)
                    .tappable(onClick = { onToggle(subtask) })
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (subtask.isDone) AshIcons.CheckCircle else AshIcons.Circle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (subtask.isDone) colors.success else colors.text3
                )
                Text(
                    text = subtask.title,
                    style = AshTheme.type.subhead,
                    textDecoration = if (subtask.isDone) TextDecoration.LineThrough else null,
                    color = if (subtask.isDone) colors.text3 else colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Метка переносов: жёлтая с 3, красная с 5 (п. 1). */
@Composable
private fun StaleBadge(task: Task) {
    val colors = AshTheme.colors
    val color = when (task.staleLevel) {
        StaleLevel.NONE -> return
        StaleLevel.WARNING -> colors.warm
        StaleLevel.CRITICAL -> colors.danger
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), AshShapes.pill)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text = "×${task.postponeCount}", style = AshTheme.type.caption, color = color)
    }
}

@Composable
private fun buildMeta(task: Task, today: LocalDate): String {
    val parts = mutableListOf<String>()
    task.dueDate?.let { date ->
        parts += when (date) {
            today -> stringResource(R.string.components_segodnya)
            today.plusDays(1) -> stringResource(R.string.ritual_zavtra)
            today.minusDays(1) -> stringResource(R.string.components_vchera)
            else -> {
                val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, RU_LOCALE)
                "$dayOfWeek, ${date.format(DATE_FORMAT)}"
            }
        }
    }
    val is24Hour = dev.ashwake.ui.theme.LocalIs24Hour.current
    task.dueTime?.let { parts += dev.ashwake.ui.theme.formatTime(it, is24Hour) }
    task.estimateMinutes?.let { parts += formatEstimateRu(it) }
    if (task.subtasks.isNotEmpty()) {
        parts += "${task.subtasks.count { it.isDone }}/${task.subtasks.size}"
    }
    task.delegatedTo?.let { parts += "→ $it" }
    return parts.joinToString(" · ")
}

@Composable
private fun formatEstimate(minutes: Int): String = formatEstimateRu(minutes)
