package dev.ashwake.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.tasks.EisenhowerQuadrant
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.theme.PriorityColors
import java.time.LocalDate
import kotlin.math.roundToInt

private val QUADRANT_TITLES = mapOf(
    EisenhowerQuadrant.URGENT_IMPORTANT to ("Срочно и важно" to "сделать"),
    EisenhowerQuadrant.NOT_URGENT_IMPORTANT to ("Важно, не срочно" to "запланировать"),
    EisenhowerQuadrant.URGENT_NOT_IMPORTANT to ("Срочно, не важно" to "делегировать"),
    EisenhowerQuadrant.NOT_URGENT_NOT_IMPORTANT to ("Не срочно, не важно" to "удалить")
)

/**
 * Матрица Эйзенхауэра с перетаскиванием между квадрантами (п. 1).
 *
 * Координаты меряются в системе корня (`boundsInRoot`), а не внутри ячейки:
 * иначе при переносе задачи в соседний квадрант точка отпускания считается
 * относительно исходной ячейки и попадание не определяется.
 */
@Composable
fun EisenhowerMatrix(
    tasks: List<Task>,
    today: LocalDate,
    quadrantOf: (Task) -> EisenhowerQuadrant,
    onMove: (Task, EisenhowerQuadrant) -> Unit,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(tasks, today) { tasks.groupBy(quadrantOf) }
    val quadrantBounds = remember { mutableStateMapOf<EisenhowerQuadrant, Rect>() }

    var draggedTask by remember { mutableStateOf<Task?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    val hoveredQuadrant = draggedTask?.let {
        quadrantBounds.entries.firstOrNull { (_, rect) -> rect.contains(dragPosition) }?.key
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            listOf(
                EisenhowerQuadrant.URGENT_IMPORTANT to EisenhowerQuadrant.NOT_URGENT_IMPORTANT,
                EisenhowerQuadrant.URGENT_NOT_IMPORTANT to EisenhowerQuadrant.NOT_URGENT_NOT_IMPORTANT
            ).forEach { (left, right) ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    listOf(left, right).forEach { quadrant ->
                        QuadrantCell(
                            quadrant = quadrant,
                            tasks = grouped[quadrant].orEmpty(),
                            isHovered = hoveredQuadrant == quadrant,
                            draggedTaskId = draggedTask?.id,
                            modifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
                            onBounds = { quadrantBounds[quadrant] = it },
                            onTaskClick = onTaskClick,
                            onDragStart = { task, startPosition ->
                                draggedTask = task
                                dragPosition = startPosition
                            },
                            onDrag = { delta -> dragPosition += delta },
                            onDragEnd = {
                                val task = draggedTask
                                val target = quadrantBounds.entries
                                    .firstOrNull { (_, rect) -> rect.contains(dragPosition) }?.key
                                if (task != null && target != null) onMove(task, target)
                                draggedTask = null
                            }
                        )
                    }
                }
            }
        }

        // Перетаскиваемая карточка рисуется поверх сетки, иначе её обрежет ячейка
        draggedTask?.let { task ->
            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        (dragPosition.x - rootOrigin.x).roundToInt() - 60,
                        (dragPosition.y - rootOrigin.y).roundToInt() - 20
                    )
                }
            ) {
                TaskChip(task = task, modifier = Modifier.widthIn(max = 160.dp))
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    quadrant: EisenhowerQuadrant,
    tasks: List<Task>,
    isHovered: Boolean,
    draggedTaskId: Long?,
    modifier: Modifier,
    onBounds: (Rect) -> Unit,
    onTaskClick: (Task) -> Unit,
    onDragStart: (Task, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val (title, action) = QUADRANT_TITLES.getValue(quadrant)
    val accent = PriorityColors[quadrant.ordinal]

    Column(
        modifier = modifier
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isHovered) accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isHovered) 2.dp else 1.dp,
                color = if (isHovered) accent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Text(
                text = "  $title",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = tasks.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = action,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tasks, key = { it.id }) { task ->
                var chipOrigin by remember(task.id) { mutableStateOf(Offset.Zero) }
                TaskChip(
                    task = task,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (draggedTaskId == task.id) 0.3f else 1f)
                        .onGloballyPositioned { chipOrigin = it.positionInRoot() }
                        .pointerInput(task.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { local -> onDragStart(task, chipOrigin + local) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount)
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd
                            )
                        }
                        .clickable { onTaskClick(task) }
                )
            }
        }
    }
}

@Composable
private fun TaskChip(task: Task, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PriorityColors[task.priority.ordinal])
        )
        Text(
            text = "  ${task.title}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
