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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.repository.tasks.TaskFilter
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Строка фильтров над списком: выполненные, проекты, теги.
 * Проекты и теги в одной ленте намеренно — это один и тот же жест
 * «сузить список», разделять их на два ряда незачем.
 */
@Composable
fun TaskFilterRow(
    filter: TaskFilter,
    projects: List<Project>,
    tags: List<Tag>,
    onToggleDone: () -> Unit,
    onProjectSelected: (Long?) -> Unit,
    onTagSelected: (Long?) -> Unit,
    onManageProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = filter.includeDone,
                onClick = onToggleDone,
                label = { Text(stringResource(R.string.components_vypolnennye)) }
            )
        }
        items(projects, key = { "p${it.id}" }) { project ->
            FilterChip(
                selected = filter.projectId == project.id,
                onClick = {
                    onProjectSelected(if (filter.projectId == project.id) null else project.id)
                },
                label = { Text(project.name) }
            )
        }
        items(tags, key = { "t${it.id}" }) { tag ->
            FilterChip(
                selected = filter.tagId == tag.id,
                onClick = { onTagSelected(if (filter.tagId == tag.id) null else tag.id) },
                label = { Text("#${tag.name}") }
            )
        }
        item {
            AssistChip(
                onClick = onManageProjects,
                label = { Text(stringResource(R.string.components_proekty)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
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
                        "Пока ни одного проекта",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.components_novyy_proekt)) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_gotovo)) } }
    )
}
