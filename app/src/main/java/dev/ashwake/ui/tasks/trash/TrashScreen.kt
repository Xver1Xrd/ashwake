package dev.ashwake.ui.tasks.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.R
import dev.ashwake.ui.components.ActionSheet
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.EmptyState
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.appHazeSource
import dev.ashwake.ui.theme.AshTheme

/**
 * Корзина задач.
 *
 * Свайп влево удаляет задачу одним движением, и промахнуться по нему легко.
 * Поэтому удаление — не DELETE, а перевод в корзину: список здесь и есть
 * обещанная в ТЗ возможность восстановления. Через 30 дней задачи
 * вычищаются сами, чтобы корзина не превращалась во вторую базу.
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    var confirmEmpty by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .appHazeSource()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(
                icon = AshIcons.ArrowBack,
                contentDescription = stringResource(R.string.trash_back),
                onClick = onBack
            )
            Text(
                text = stringResource(R.string.trash_title),
                style = AshTheme.type.title2,
                color = colors.text,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            if (tasks.isNotEmpty()) {
                TextAction(
                    text = stringResource(R.string.trash_empty_action),
                    color = colors.danger,
                    onClick = { confirmEmpty = true }
                )
            }
        }

        if (tasks.isEmpty()) {
            EmptyState(
                icon = AshIcons.Trash,
                title = stringResource(R.string.trash_empty_title),
                description = stringResource(R.string.trash_empty_description)
            )
        } else {
            ListGroup(
                items = tasks,
                footer = stringResource(R.string.trash_footer)
            ) { task ->
                ListRow(
                    title = task.title,
                    subtitle = null,
                    titleColor = colors.text2,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextAction(
                                text = stringResource(R.string.trash_restore),
                                onClick = { viewModel.restore(task.id) }
                            )
                            TextAction(
                                text = stringResource(R.string.trash_purge),
                                color = colors.danger,
                                onClick = { viewModel.purge(task.id) }
                            )
                        }
                    }
                )
            }
        }

        Column(Modifier.padding(horizontal = ScreenPadding)) {}
    }

    if (confirmEmpty) {
        ActionSheet(
            title = stringResource(R.string.trash_confirm_title),
            message = stringResource(R.string.trash_confirm_message),
            confirmText = stringResource(R.string.trash_confirm_action),
            onConfirm = {
                viewModel.emptyTrash()
                confirmEmpty = false
            },
            onDismiss = { confirmEmpty = false }
        )
    }
}
