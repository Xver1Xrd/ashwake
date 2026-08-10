package dev.ashwake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshTheme

/**
 * Верхняя панель экрана.
 *
 * Материаловский `TopAppBar` выравнивает заголовок по левому краю и оставляет
 * под него 64dp — на экране формы это полоса пустоты. Здесь панель низкая,
 * заголовок по центру, кнопка «назад» — иконка без подложки.
 *
 * Панель не прокручивается вместе с содержимым и не размывает его: размытие
 * стоит денег на каждом кадре, а выигрывает от него только та панель, под
 * которую действительно что-то уезжает.
 */
private val NavBarHeight = 48.dp

/**
 * Крупный заголовок раздела: то, чем начинается каждая вкладка.
 *
 * Один и тот же блок на всех четырёх вкладках — это и есть то, по чему
 * приложение узнаётся как одно целое. Материаловский `TopAppBar` с
 * заголовком в 22sp и полосой на всю ширину такого впечатления не даёт.
 */
@Composable
fun AshLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AshTheme.type.largeTitle,
                color = AshTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(text = it, style = AshTheme.type.subhead, color = AshTheme.colors.text2)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = actions
        )
    }
}

@Composable
fun AshNavBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(NavBarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (onBack != null) {
            IconAction(
                icon = AshIcons.ChevronLeft,
                contentDescription = "Назад",
                onClick = onBack
            )
        } else {
            Box(Modifier.size(44.dp))
        }

        Text(
            text = title,
            style = AshTheme.type.headline,
            color = AshTheme.colors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = actions
        )
    }
}
