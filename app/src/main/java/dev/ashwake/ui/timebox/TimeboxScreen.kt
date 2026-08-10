package dev.ashwake.ui.timebox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.model.tasks.BlockKind
import dev.ashwake.domain.model.tasks.TimeboxBlock
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import dev.ashwake.ui.theme.Steel
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE")
private val HOUR_HEIGHT = 64.dp

/** Шаг перетаскивания: пятиминутки, чтобы блоки не вставали на 13:07. */
private const val SNAP_MINUTES = 5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeboxScreen(viewModel: TimeboxViewModel = hiltViewModel()) {
    val date by viewModel.date.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    val settings by viewModel.timeboxSettings.collectAsStateWithLifecycle()
    val useCalendar by viewModel.useCalendar.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val planning by viewModel.planning.collectAsStateWithLifecycle()

    var selectedBlock by remember { mutableStateOf<TimeboxBlock?>(null) }

    Scaffold(
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = stringResource(R.string.timebox_shkala_dnya),
                actions = {
                    IconButton(onClick = { viewModel.shiftDate(-1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.detail_nazad))
                    }
                    IconButton(onClick = { viewModel.shiftDate(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.calendar_vpered))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::planDay,
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                text = { Text(if (planning) "Раскладываю…" else "Разложить день") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                date.format(DATE_FORMAT).replaceFirstChar { it.uppercase() },
                style = AshTheme.type.headline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${settings.workStartMinute / 60}:00–${settings.workEndMinute / 60}:00 · буфер ${settings.bufferMinutes} мин",
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )
            }

            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 5, 10, 15).forEach { buffer ->
                    FilterChip(
                        selected = settings.bufferMinutes == buffer,
                        onClick = { viewModel.setBuffer(buffer) },
                        label = { Text("буфер $buffer") }
                    )
                }
                FilterChip(
                    selected = useCalendar,
                    onClick = { viewModel.setUseCalendar(!useCalendar) },
                    label = { Text(stringResource(R.string.timebox_kalendar)) }
                )
            }

            if (useCalendar && !viewModel.calendarPermissionGranted()) {
                Text(
                    "Нет доступа к календарю — события учитываться не будут. Разрешение запрашивается в настройках системы",
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (day.deficitMinutes > 0) {
                Text(
                    "Не влезло ${formatDuration(day.deficitMinutes)} — часть задач придётся вынести на завтра",
                    style = AshTheme.type.footnote,
                    color = Ember,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            DayGrid(
                blocks = day.blocks,
                workStartMinute = settings.workStartMinute,
                workEndMinute = settings.workEndMinute,
                onMove = viewModel::moveBlock,
                onSelect = { selectedBlock = it }
            )

            TextButton(
                onClick = viewModel::clearDay,
                modifier = Modifier.padding(16.dp)
            ) { Text(stringResource(R.string.timebox_ochistit_den)) }
        }
    }

    outcome?.let { data ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOutcome,
            title = { Text(if (data.deficitMinutes == 0) "День разложен" else "День не вмещает всё") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (data.deficitMinutes > 0) {
                        Text(
                            "Не хватает ${formatDuration(data.deficitMinutes)}",
                            style = AshTheme.type.callout,
                            color = Ember
                        )
                        Text(stringResource(R.string.timebox_na_zavtra_stoit_vynesti), style = AshTheme.type.subhead)
                        data.deferredTitles.take(6).forEach {
                            Text("· $it", style = AshTheme.type.subhead)
                        }
                    } else {
                        Text(stringResource(R.string.timebox_vse_pomestilos), style = AshTheme.type.callout)
                    }

                    if (data.withoutEstimateTitles.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            "Без оценки времени — не раскладываются:",
                            style = AshTheme.type.subhead
                        )
                        data.withoutEstimateTitles.take(6).forEach {
                            Text(
                                "· $it",
                                style = AshTheme.type.subhead,
                                color = AshTheme.colors.text2
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissOutcome) { Text(stringResource(R.string.detail_ponyatno)) } }
        )
    }

    selectedBlock?.let { block ->
        BlockActionsDialog(
            block = block,
            onPin = { viewModel.togglePin(block.id, !block.pinned); selectedBlock = null },
            onOverran = { minutes ->
                viewModel.markOverran(block.id, block.endMinute + minutes)
                selectedBlock = null
            },
            onDelete = { viewModel.deleteBlock(block.id); selectedBlock = null },
            onDismiss = { selectedBlock = null }
        )
    }
}

/**
 * Шкала дня с перетаскиванием.
 *
 * Блок берётся долгим тапом и двигается по вертикали; время округляется
 * до пяти минут. Закреплённые блоки не двигаются вовсе — на них стоит замок.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayGrid(
    blocks: List<TimeboxBlock>,
    workStartMinute: Int,
    workEndMinute: Int,
    onMove: (Long, Int) -> Unit,
    onSelect: (TimeboxBlock) -> Unit
) {
    val density = LocalDensity.current
    val startHour = (workStartMinute / 60).coerceAtMost(23)
    val endHour = ((workEndMinute + 59) / 60).coerceAtLeast(startHour + 1)
    val hours = endHour - startHour

    var draggedId by remember { mutableLongStateOf(0L) }
    var dragOffsetMinutes by remember { mutableStateOf(0) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT * hours)
    ) {
        repeat(hours) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HOUR_HEIGHT)
                    .offset(y = HOUR_HEIGHT * index)
            ) {
                Text(
                    "%02d:00".format(startHour + index),
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2,
                    modifier = Modifier.width(44.dp).padding(start = 8.dp, top = 2.dp)
                )
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }

        blocks.forEach { block ->
            val isDragged = draggedId == block.id
            val minuteOffset = block.startMinute - workStartMinute +
                if (isDragged) dragOffsetMinutes else 0

            Box(
                modifier = Modifier
                    .padding(start = 52.dp, end = 12.dp)
                    .offset(y = minuteHeight(minuteOffset))
                    .fillMaxWidth()
                    .height(minuteHeight(block.durationMinutes).coerceAtLeast(22.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorOf(block.kind).copy(alpha = if (isDragged) 0.5f else 0.22f))
                    .border(1.dp, colorOf(block.kind), RoundedCornerShape(8.dp))
                    .alpha(if (block.pinned) 0.9f else 1f)
                    .then(
                        if (block.pinned) Modifier
                        else Modifier.pointerInput(block.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedId = block.id
                                    dragOffsetMinutes = 0
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val minutes = with(density) {
                                        (amount.y / HOUR_HEIGHT.toPx() * 60).roundToInt()
                                    }
                                    dragOffsetMinutes += minutes
                                },
                                onDragEnd = {
                                    val snapped = ((block.startMinute + dragOffsetMinutes)
                                        .toFloat() / SNAP_MINUTES).roundToInt() * SNAP_MINUTES
                                    onMove(block.id, snapped.coerceAtLeast(0))
                                    draggedId = 0L
                                    dragOffsetMinutes = 0
                                },
                                onDragCancel = {
                                    draggedId = 0L
                                    dragOffsetMinutes = 0
                                }
                            )
                        }
                    )
                    .combinedClickable(
                        onClick = { onSelect(block) },
                        onLongClick = {}
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (block.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.timebox_zakreplen),
                            modifier = Modifier.width(14.dp),
                            tint = colorOf(block.kind)
                        )
                    }
                    Text(
                        text = " ${block.startTime()} ${block.title}",
                        style = AshTheme.type.footnote,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockActionsDialog(
    block: TimeboxBlock,
    onPin: () -> Unit,
    onOverran: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(block.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${block.startTime()}–${block.endTime()} · " +
                        formatDuration(block.durationMinutes),
                    style = AshTheme.type.callout
                )
                TextButton(onClick = onPin) {
                    Text(if (block.pinned) "Открепить" else "Закрепить: не двигать")
                }
                Text(stringResource(R.string.timebox_zatyanulos_na), style = AshTheme.type.subhead)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60).forEach { minutes ->
                        TextButton(onClick = { onOverran(minutes) }) { Text("+$minutes") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.components_zakryt)) } },
        dismissButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.timebox_ubrat_blok)) } }
    )
}

@Composable
private fun minuteHeight(minutes: Int): Dp = HOUR_HEIGHT * (minutes / 60f)

@Composable
private fun colorOf(kind: BlockKind): Color = when (kind) {
    BlockKind.TASK -> Steel
    BlockKind.ROUTINE -> Moss
    BlockKind.CALENDAR -> Gold
    BlockKind.LUNCH -> Ember
    BlockKind.MANUAL -> Steel
}

internal fun formatDuration(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    return when {
        safe < 60 -> "$safe мин"
        safe % 60 == 0 -> "${safe / 60} ч"
        else -> "${safe / 60} ч ${safe % 60} мин"
    }
}
