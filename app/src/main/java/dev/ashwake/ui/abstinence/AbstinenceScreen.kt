package dev.ashwake.ui.abstinence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.R
import dev.ashwake.ui.abstinence.components.LiveCounter
import dev.ashwake.ui.abstinence.components.currencySymbol
import dev.ashwake.ui.abstinence.components.formatMoney
import dev.ashwake.ui.abstinence.editor.CreateAbstinenceDialog
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.EmptyState
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.appHazeSource
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import kotlin.math.roundToInt

/**
 * Список отказов.
 *
 * Каждый отказ — большая карточка со счётчиком: в отличие от задачи, отказ
 * не строка в списке, а состояние, на которое смотрят. Поэтому карточка
 * занимает ширину экрана и подсвечена холодным градиентом — тем же цветом,
 * которым в приложении помечены все счётчики и таймеры.
 */
@Composable
fun AbstinenceScreen(
    onOpen: (Long) -> Unit,
    viewModel: AbstinenceViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    var showCreate by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().appHazeSource(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.abstinence_otkazy),
                        style = AshTheme.type.largeTitle,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    IconAction(
                        icon = AshIcons.Add,
                        contentDescription = stringResource(R.string.abstinence_novyy_otkaz),
                        onClick = { showCreate = true }
                    )
                }
            }

            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = AshIcons.Prohibit,
                        title = stringResource(R.string.abstinence_schetchikov_poka_net),
                        description = "Отказ не надо делать — надо не делать. " +
                            "Счётчик идёт сам, вмешиваться нужно только при срыве",
                        actionText = stringResource(R.string.abstinence_novyy_otkaz),
                        onAction = { showCreate = true }
                    )
                }
            }

            items(items, key = { it.abstinence.id }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.cold.copy(alpha = 0.18f),
                                    colors.surface1
                                )
                            ),
                            AshShapes.card
                        )
                        .tappable(onClick = { onOpen(item.abstinence.id) })
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.abstinence.name,
                        style = AshTheme.type.title3,
                        color = colors.text
                    )
                    LiveCounter(
                        duration = item.stats.current,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Badge("рекорд ${item.stats.record.toDays()}")
                        Badge("попытка №${item.stats.attemptNumber}")
                    }
                    item.stats.savings?.let { savings ->
                        Row(
                            Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                AshIcons.Coins,
                                contentDescription = null,
                                tint = colors.warm,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${formatMoney(savings.money)} " +
                                    currencySymbol(savings.currency) +
                                    " · не ${savings.units.roundToInt()} ${savings.unitName}",
                                style = AshTheme.type.footnote,
                                color = colors.warm
                            )
                        }
                    }
                }
            }
        }

        if (showCreate) {
            CreateAbstinenceDialog(
                onCreate = { name, icon, iconPath, mode, startedAt, motivation, baseline, subs ->
                    viewModel.create(
                        name, icon, iconPath, mode, startedAt, motivation, baseline, subs
                    )
                    showCreate = false
                },
                onDismiss = { showCreate = false }
            )
        }
    }
}

/** Подпись-таблетка под счётчиком: рекорд, номер попытки. */
@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2,
        modifier = Modifier
            .background(AshTheme.colors.surface2, AshShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
