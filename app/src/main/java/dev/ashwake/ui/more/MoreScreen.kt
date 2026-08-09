package dev.ashwake.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.ListDivider
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.appHazeSource
import dev.ashwake.ui.theme.AshTheme
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * «Ещё» — четвёртая вкладка.
 *
 * Панель вкладок держит четыре раздела, а не семь: остальное живёт здесь
 * обычным сгруппированным списком. Это тот же строительный блок, что и
 * список задач, и настройки — одна метафора на всё приложение.
 */
@Composable
fun MoreScreen(
    onOpenAbstinence: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenTimers: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenRitual: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = AshTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .appHazeSource()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.more_esche),
            style = AshTheme.type.largeTitle,
            color = colors.text,
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
        )

        ListGroup(header = stringResource(R.string.more_razdely)) {
            MoreRow("Отказы", AshIcons.Prohibit, onOpenAbstinence)
            ListDivider(52.dp)
            MoreRow("Персонаж", AshIcons.Person, onOpenCharacter)
            ListDivider(52.dp)
            MoreRow("Таймеры и рутины", AshIcons.Timer, onOpenTimers)
            ListDivider(52.dp)
            MoreRow("Статистика", AshIcons.BarChart, onOpenStats)
        }

        ListGroup(header = stringResource(R.string.more_vecher)) {
            MoreRow("Вечерний ритуал", AshIcons.Moon, onOpenRitual)
        }

        ListGroup(
            header = stringResource(R.string.more_prilozhenie),
            footer = stringResource(R.string.more_prilozhenie_rabotaet_oflayn_ni_odnogo_setevo)
        ) {
            MoreRow("Настройки", AshIcons.Settings, onOpenSettings)
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
