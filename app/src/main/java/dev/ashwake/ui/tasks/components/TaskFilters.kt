package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.tasks.TaskFilter
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Строка фильтров над списком: только выполненные и теги, указанные в задачах.
 */
@Composable
fun TaskFilterRow(
    filter: TaskFilter,
    tasks: List<Task>,
    tags: List<Tag>,
    onToggleDone: () -> Unit,
    onTagSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val usedTagIds = remember(tasks) {
        tasks.flatMap { it.tags }.map { it.id }.toSet()
    }
    val relevantTags = remember(tags, usedTagIds) {
        tags.filter { it.id in usedTagIds }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            ChipButton(
                text = stringResource(R.string.components_vypolnennye),
                selected = filter.includeDone,
                onClick = onToggleDone
            )
        }
        items(relevantTags, key = { "t${it.id}" }) { tag ->
            ChipButton(
                text = "#${tag.name}",
                selected = filter.tagId == tag.id,
                onClick = { onTagSelected(if (filter.tagId == tag.id) null else tag.id) }
            )
        }
    }
}

@Composable
fun ProjectsDialog(
    projects: List<Project>,
    onCreate: (String) -> Unit,
    onArchive: (Project) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.components_proekty)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (projects.isEmpty()) {
                    Text(
                        stringResource(R.string.components_poka_ni_odnogo_proekta),
                        style = AshTheme.type.callout,
                        color = AshTheme.colors.text2
                    )
                }
                projects.forEach { project ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(project.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onArchive(project) }) {
                            Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.editor_v_arhiv))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AshTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = stringResource(R.string.components_novyy_proekt),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCreate(newName); newName = "" },
                        enabled = newName.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.editor_sozdat))
                    }
                }
            }
        },
        confirmButton = { TextAction(text = stringResource(R.string.editor_gotovo), onClick = onDismiss) }
    )
}
