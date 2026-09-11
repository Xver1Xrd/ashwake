package dev.ashwake.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.R
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListDivider
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.appHazeSource
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * «Ещё» — четвёртая вкладка.
 *
 * Содержит глобальный поиск и фильтрацию по истории (задачи, архив, заметки),
 * а также навигацию по остальным разделам приложения.
 */
@Composable
fun MoreScreen(
    onOpenAbstinence: () -> Unit,
    onOpenTimers: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenRitual: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenTask: (Long) -> Unit = {},
    viewModel: MoreViewModel = hiltViewModel()
) {
    val colors = AshTheme.colors
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .appHazeSource()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.more_esche),
            style = AshTheme.type.largeTitle,
            color = colors.text,
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)
        )

        // Поле поиска по истории
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AshTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = "Поиск по задачам, архиву и заметкам...",
                singleLine = true,
                trailing = {
                    if (query.isNotEmpty()) {
                        IconAction(
                            icon = AshIcons.Close,
                            contentDescription = "Очистить",
                            tint = colors.text3,
                            onClick = { viewModel.setQuery("") }
                        )
                    }
                }
            )

            // Фильтры поиска
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { f ->
                    ChipButton(
                        text = f.title,
                        selected = filter == f,
                        onClick = { viewModel.setFilter(f) }
                    )
                }
            }
        }

        if (query.isNotBlank()) {
            // Результаты поиска
            ListGroup(
                header = "Найдено: ${searchResults.size}",
                footer = if (searchResults.isEmpty()) "По вашему запросу ничего не найдено" else null
            ) {
                searchResults.forEachIndexed { index, item ->
                    SearchResultRow(
                        item = item,
                        onClick = {
                            item.taskId?.let { onOpenTask(it) }
                        }
                    )
                    if (index != searchResults.lastIndex) {
                        ListDivider(56.dp)
                    }
                }
            }
        } else {
            // Обычные разделы «Ещё»
            ListGroup(header = stringResource(R.string.more_razdely)) {
                MoreRow(stringResource(R.string.abstinence_otkazy), AshIcons.Prohibit, onOpenAbstinence)
                ListDivider(52.dp)
                MoreRow(stringResource(R.string.more_taymery_i_rutiny), AshIcons.Timer, onOpenTimers)
                ListDivider(52.dp)
                MoreRow(stringResource(R.string.stats_statistika), AshIcons.BarChart, onOpenStats)
            }

            ListGroup(header = stringResource(R.string.more_zadachi_group)) {
                MoreRow(stringResource(R.string.more_trash), AshIcons.Trash, onOpenTrash)
            }

            ListGroup(header = stringResource(R.string.more_vecher)) {
                MoreRow(stringResource(R.string.ritual_vecherniy_ritual), AshIcons.Moon, onOpenRitual)
            }

            ListGroup(
                header = stringResource(R.string.more_prilozhenie),
                footer = stringResource(R.string.more_prilozhenie_rabotaet_oflayn_ni_odnogo_setevo)
            ) {
                MoreRow(stringResource(R.string.character_nastroyki), AshIcons.Settings, onOpenSettings)
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchResultItem,
    onClick: () -> Unit
) {
    val colors = AshTheme.colors
    val (icon, iconTint) = when (item.type) {
        HistoryItemType.TASK -> AshIcons.CheckCircle to colors.success
        HistoryItemType.ARCHIVE -> AshIcons.Trash to colors.danger
        HistoryItemType.NOTE -> AshIcons.Moon to colors.warm
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(AshShapes.small)
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = AshTheme.type.body,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle?.let {
                Text(
                    text = it,
                    style = AshTheme.type.footnote,
                    color = colors.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item.date?.let {
            Text(
                text = it,
                style = AshTheme.type.caption,
                color = colors.text3
            )
        }
    }
}

@Composable
private fun MoreRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListRow(
        title = title,
        showChevron = true,
        onClick = onClick,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AshTheme.colors.accent,
                modifier = Modifier.size(24.dp)
            )
        }
    )
}
