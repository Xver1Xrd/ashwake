package dev.ashwake.ui.timers

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.SegmentedControl
import dev.ashwake.ui.focus.FocusScreen
import dev.ashwake.ui.routines.RoutinesScreen
import dev.ashwake.ui.theme.AshTheme

/**
 * Рутины и фокус под одной вкладкой.
 *
 * Обе фичи — таймеры, и разносить их по отдельным пунктам нижней навигации
 * значило бы занять два места из пяти под одно и то же действие «запустить время».
 *
 * Заголовок здесь один на оба раздела: у вложенных экранов своей панели нет.
 * Две панели друг под другом съедали бы треть экрана и выглядели бы как
 * два приложения, вставленных одно в другое.
 */
@Composable
fun TimersScreen(onRunRoutine: () -> Unit, onBack: () -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.routines_rutiny), stringResource(R.string.focus_fokus))

    Column(
        Modifier
            .fillMaxSize()
            .background(AshTheme.colors.background)
    ) {
        AshNavBar(title = tabs[tab], onBack = onBack)

        SegmentedControl(
            options = tabs,
            selectedIndex = tab,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            onSelect = { tab = it }
        )

        when (tab) {
            0 -> RoutinesScreen(onRun = onRunRoutine)
            else -> FocusScreen()
        }
    }
}
