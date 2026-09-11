package dev.ashwake.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.rememberHaptics
import androidx.compose.ui.res.stringResource
import dev.ashwake.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.platform.service.formatTime

/** Редактор рутины: название и список шагов с длительностями (п. 6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    onDone: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel()
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAddStep by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routine_editor_nazvanie)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_nazad))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onDone) }) { Text(stringResource(R.string.routine_editor_sohranit)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.routine_editor_nazvanie)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (steps.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.routine_editor_dobavte_hotya_by_odin_shag),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(steps, key = { index, _ -> "step-$index" }) { index, step ->
                StepRow(
                    index = index,
                    totalCount = steps.size,
                    title = step.title,
                    minutes = step.durationSeconds / 60,
                    onTitleChange = { viewModel.updateStepTitle(index, it) },
                    onMinutesChange = { viewModel.updateStepDuration(index, it) },
                    onMove = { delta -> viewModel.moveStep(index, delta) },
                    onDelete = { viewModel.removeStep(index) }
                )
            }

            item {
                FilledTonalButton(
                    onClick = { showAddStep = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.routine_editor_dobavit_shag))
                }
            }
        }
    }

    if (showAddStep) {
        AddStepDialog(
            onDismiss = { showAddStep = false },
            onAdd = { title, minutes ->
                viewModel.addStep(title, minutes)
                showAddStep = false
            }
        )
    }
}

@Composable
private fun StepRow(
    index: Int,
    totalCount: Int,
    title: String,
    minutes: Int,
    onTitleChange: (String) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }

    Column(
        Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                if (isDragging) {
                    shadowElevation = 8.dp.toPx()
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Шаг ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Перетащить",
                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .pointerInput(index, totalCount) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDragging = true
                                haptics.play(HapticKind.LIGHT)
                            },
                            onDragEnd = {
                                isDragging = false
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                if (dragOffsetY > thresholdPx && index < totalCount - 1) {
                                    onMove(1)
                                    haptics.play(HapticKind.LIGHT)
                                    dragOffsetY -= thresholdPx
                                } else if (dragOffsetY < -thresholdPx && index > 0) {
                                    onMove(-1)
                                    haptics.play(HapticKind.LIGHT)
                                    dragOffsetY += thresholdPx
                                }
                            }
                        )
                    }
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = { Text(stringResource(R.string.routine_editor_nazvanie_shaga)) },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Text("↑") }
            IconButton(onClick = { onMove(1) }, enabled = index < totalCount - 1) { Text("↓") }
            TextButton(onClick = { onMinutesChange(minutes - 1) }) { Text("−") }
            Text("$minutes мин", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onMinutesChange(minutes + 1) }) { Text("+") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.routine_editor_udalit_shag))
            }
        }
    }
}

@Composable
private fun AddStepDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, minutes: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_editor_novy_shag)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.routine_editor_nazvanie)) },
                    singleLine = false,
                    maxLines = 3
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.routine_editor_minuty)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, minutes.toIntOrNull() ?: 1) },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.routine_editor_dobavit_shag)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_otmena)) }
        }
    )
}
